package com.lagodiuk.decisiontree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class RandomForest {

	private List<DecisionTree> trees = new ArrayList<DecisionTree>();

	public Map<Object, Integer> classify(Item item) {
		Map<Object, Integer> result = new HashMap<Object, Integer>();

		for (DecisionTree tree : this.trees) {
			Object category = tree.classify(item);

			Integer count = result.get(category);
			count = (count == null) ? 1 : count + 1;

			result.put(category, count);
		}
		return result;
	}

	public static RandomForest create(DecisionTreeBuilder builder, int size) {
		RandomForest forest = new RandomForest();

		List<Item> items = new ArrayList<Item>(builder.getTrainingSet());

		Collections.shuffle(items);

		int chunkSize = items.size() / size;

		for (int i = 0; i < size; i++) {
			List<Item> trainingSet = new ArrayList<Item>();

			trainingSet.addAll(items.subList(i * chunkSize, (i + 1) * chunkSize));
			trainingSet.addAll(getRandomSubset(items, i + 1));

			DecisionTree tree = builder.setTrainingSet(trainingSet).createDecisionTree().mergeRedundantRules();
			forest.trees.add(tree);
		}

		return forest;
	}

	private static List<Item> getRandomSubset(List<Item> items, int numberOfTree) {
		int itemsCount = items.size();

		List<Item> result = new LinkedList<Item>();
		for (int i = 0; i < itemsCount; i++) {
			if ((i % numberOfTree) != 0) {
				result.add(items.get(i));
			}
		}

		return result;
	}

}
