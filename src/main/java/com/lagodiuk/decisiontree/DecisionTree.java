/*******************************************************************************
 * The MIT License (MIT)
 * 
 * Copyright (c) 2013 Yurii Lahodiuk (yura_lagodiuk@ukr.net)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 ******************************************************************************/
package com.lagodiuk.decisiontree;

import java.util.*;
import java.util.Map.Entry;

public class DecisionTree {

	private Rule rule;

	private Object category;

	private DecisionTree matchSubTree;

	private DecisionTree notMatchSubTree;

	public Object classify(Item item) {
		if (this.isLeaf()) {
			return this.getCategory();
		} else {
			if (this.getRule().match(item)) {
				return this.getMatchSubTree().classify(item);
			} else {
				return this.getNotMatchSubTree().classify(item);
			}
		}
	}

	public DecisionTree mergeRedundantRules() {

		if (this.getMatchSubTree() != null) {
			this.getMatchSubTree().mergeRedundantRules();
		}

		if (this.getNotMatchSubTree() != null) {
			this.getNotMatchSubTree().mergeRedundantRules();
		}

		if ((this.getRule() != null)
				&& (this.getMatchSubTree().getCategory() != null)
				&& (this.getNotMatchSubTree().getCategory() != null)
				&& (this.getMatchSubTree().getCategory().equals(this.getNotMatchSubTree().getCategory()))) {

			this.setCategory(this.getMatchSubTree().getCategory());
			this.setRule(null);
			this.setMatchSubTree(null);
			this.setNotMatchSubTree(null);
		}

		return this;
	}

    public void accept(DecisionTreeVisitor visitor) {
		visitor.visit(this);
	}

	public boolean isLeaf() {
		return this.getRule() == null;
	}

	public Object getCategory() {
		return this.category;
	}

	public Rule getRule() {
		return this.rule;
	}

	public DecisionTree getMatchSubTree() {
		return this.matchSubTree;
	}

	public DecisionTree getNotMatchSubTree() {
		return this.notMatchSubTree;
	}

	public static DecisionTreeBuilder createBuilder() {
		return new DecisionTreeBuilder();
	}

	public static DecisionTree buildDecisionTree(
			List<Item> items,
			int minimalNumberOfItems,
			Map<String, List<Predicate>> attributesPredicates,
			List<Predicate> defaultPredicates,
			Set<String> ignoredAttributes
    ) {

		if (items.size() <= minimalNumberOfItems) {
			return makeLeaf(items);
		}

		double entropy = entropy(items);

		if (Double.compare(entropy, 0) == 0) {
			// all categories the same
			return makeLeaf(items);
		}

		SplitResult splitResult = findBestSplit(items, attributesPredicates, defaultPredicates, ignoredAttributes);

		if (splitResult == null) {
			// can't find split which reduces entropy
			return makeLeaf(items);
		}

		DecisionTree matchSubTree =
				buildDecisionTree(splitResult.matched, minimalNumberOfItems, attributesPredicates, defaultPredicates, ignoredAttributes);

		DecisionTree notMatchSubTree =
				buildDecisionTree(splitResult.notMatched, minimalNumberOfItems, attributesPredicates, defaultPredicates, ignoredAttributes);

		DecisionTree root = new DecisionTree();
		root.setRule(splitResult.rule);
		root.setMatchSubTree(matchSubTree);
		root.setNotMatchSubTree(notMatchSubTree);
		return root;
	}

	private static DecisionTree makeLeaf(List<Item> items) {
		List<Object> categories = getCategories(items);
		Object category = getMostFrequentCategory(categories);
		DecisionTree leaf = new DecisionTree();
		leaf.setCategory(category);
		return leaf;
	}

	private static Object getMostFrequentCategory(List<Object> categories) {
		Map<Object, Integer> categoryCountMap = groupAndCount(categories);

		Object mostFrequentCategory = null;
		int mostFrequentCategoryCount = -1;

		for (Entry<Object, Integer> e : categoryCountMap.entrySet()) {
			Object category = e.getKey();
			int count = e.getValue();

			if (count >= mostFrequentCategoryCount) {
				mostFrequentCategoryCount = count;
				mostFrequentCategory = category;
			}
		}

		return mostFrequentCategory;
	}

	private static SplitResult findBestSplit(
			List<Item> items,
			Map<String, List<Predicate>> attributesPredicates,
			List<Predicate> defaultPredicates,
			Set<String> ignoredAttributes) {
		
		double initialEntropy = entropy(items);

		double bestGain = 0;

		SplitResult bestSplitResult = null;
		
		Set<Rule> testedRules = new HashSet<Rule>();
		
		for (Item baseItem : new LinkedList<Item>(items)) {
			for (String attr : baseItem.getAttributeNames()) {

				if (ignoredAttributes.contains(attr)) {
					continue;
				}

				Object value = baseItem.getFieldValue(attr);

				List<Predicate> predicates = predicatesForAttribute(attr, attributesPredicates, defaultPredicates);

				for (Predicate pred : predicates) {
					Rule rule = new Rule(attr, pred, value);
					
					// Avoid checking the slit by the same rules more than once
					if(testedRules.contains(rule)) {
						continue;
					}
					testedRules.add(rule);

					SplitResult splitResult = split(rule, items);

					double matchedEntropy = entropy(splitResult.matched);
					double notMatchedEntropy = entropy(splitResult.notMatched);

					double pMatched = (double) splitResult.matched.size() / items.size();
					double pNotMatched = (double) splitResult.notMatched.size() / items.size();

					double gain = initialEntropy - (pMatched * matchedEntropy) - (pNotMatched * notMatchedEntropy);
					if (gain > bestGain) {
						bestGain = gain;
						bestSplitResult = splitResult;
					}
				}
			}
		}
		return bestSplitResult;
	}

	private static SplitResult split(Rule rule, List<Item> base) {
		List<Item> matched = new LinkedList<Item>();
		List<Item> notMatched = new LinkedList<Item>();

		for (Item otherItem : new LinkedList<Item>(base)) {
			if (rule.match(otherItem)) {
				matched.add(otherItem);
			} else {
				notMatched.add(otherItem);
			}
		}

		return new SplitResult(matched, notMatched, rule);
	}

	private static List<Predicate> predicatesForAttribute(
			String attr,
			Map<String, List<Predicate>> attributesPredicates,
			List<Predicate> defaultPredicates) {

		List<Predicate> attrPredicates = attributesPredicates.get(attr);
		if ((attrPredicates != null) && (!attrPredicates.isEmpty())) {
			return attrPredicates;
		} else if ((defaultPredicates != null) && (!defaultPredicates.isEmpty())) {
			return defaultPredicates;
		}

		return Collections.emptyList();
	}

	private static double entropy(List<Item> items) {
		List<Object> categories = getCategories(items);
		Map<Object, Integer> categoryCount = groupAndCount(categories);
		return entropy(categoryCount.values());
	}

	private static double entropy(Collection<Integer> values) {
		double totalCount = 0;
		for (Integer count : values) {
			totalCount += count;
		}

		double entropy = 0;
		for (Integer count : values) {
			double p = count / totalCount;
			entropy += -1 * p * Math.log(p);
		}
		return entropy;
	}

	private static List<Object> getCategories(List<Item> items) {
		List<Object> categories = new LinkedList<Object>();
		for (Item item : items) {
			categories.add(item.getCategory());
		}
		return categories;
	}

	private static Map<Object, Integer> groupAndCount(List<Object> categories) {
		Map<Object, Integer> categoryCount = new HashMap<Object, Integer>();
		for (Object cat : categories) {
			Integer count = categoryCount.get(cat);
			if (count == null) {
				count = 0;
			}
			categoryCount.put(cat, count + 1);
		}
		return categoryCount;
	}

	public void setCategory(Object category) {
		this.category = category;
	}

	public void setRule(Rule rule) {
		this.rule = rule;
	}

	public void setMatchSubTree(DecisionTree matchSubTree) {
		this.matchSubTree = matchSubTree;
	}

	public void setNotMatchSubTree(DecisionTree notMatchSubTree) {
		this.notMatchSubTree = notMatchSubTree;
	}

	private static class SplitResult {

		public final Rule rule;

		public final List<Item> matched;

		public final List<Item> notMatched;

		public SplitResult(List<Item> matched, List<Item> notMatched, Rule rule) {
			this.rule = rule;
			this.matched = new ArrayList<Item>(matched);
			this.notMatched = new ArrayList<Item>(notMatched);
		}
	}

}
