package comeback;

import java.util.List;
import java.util.Map;
import java.util.Set;

import util.Iterators.CartesianCollectionIterable;
import util.Iterators.PowerSetIterable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import edu.stanford.nlp.trees.Tree;

public class FrankenTreeFactory {
	// Constants 
	private static final int ITERATION_LIMIT = 60000;
	private static final int POWERSET_LIMIT = 10000000;
	
	// 
	public Tree template;
	public List<Tree> templateNodes; 								// template nodes in a preOrderList
	public Map<Tree,Integer> nodeToIndex = Maps.newHashMap();		// map from node to index in list	
	
	List<Set<Tree>> limbventory = Lists.newArrayList();
	
	public FrankenTreeFactory (Tree template) {
		this.template = template;
		this.templateNodes = template.preOrderNodeList();
		this.nodeToIndex = buildNodeToIndexMap();
		this.limbventory = buildEmptyLimbventory();
	}

	public Set<Tree> generateAllFrankenTrees() {
		ImmutableList<Tree> immTemplateNodes = ImmutableList.<Tree>builder().addAll(templateNodes).build();
		PowerSetIterable<Tree> pit = new PowerSetIterable<Tree>(immTemplateNodes);
		if (pit.size() > POWERSET_LIMIT) {
			System.out.println("POWERSET LIMIT EXCEEDED " + " size: " + pit.size());
			return Sets.newHashSet();
		}
		Set<Tree> ret = Sets.newHashSet();
		for (List<Tree> set : pit) {
			
			if (!isValidReplacementList(set)) continue;
			
			// make limbventory
			List<Set<Tree>> relevantLimbventory = Lists.<Set<Tree>>newArrayList();
			for (int i=0; i<set.size(); i++){
				Set<Tree> limbs = Sets.newHashSet();
				if (set.get(i) != null)
					limbs.addAll(limbventory.get(i));
				relevantLimbventory.add(limbs);
			}
			ImmutableList<Set<Tree>> immRelevantLimbventory = ImmutableList.<Set<Tree>>builder()
					.addAll(relevantLimbventory)
					.build();
			ret.addAll(buildFrankenTreesAnatomically(immRelevantLimbventory));
		}
		return ret;
	}
	
	/**
	 * Assumes that replacementList is pre-order
	 * @param replacementList
	 * @return
	 */
	public boolean isValidReplacementList(List<Tree> replacementList) {
		Preconditions.checkArgument(replacementList.size() == templateNodes.size());
		for (int i=0; i<replacementList.size(); i++) {
			Tree cur = replacementList.get(i);
			if (cur == null) continue;
			for (int j=0; j<i; j++) {
				Tree pred = replacementList.get(j);
				
				if (pred == null) continue;
				
				if (cur.parent(pred) != null)
					return false;
			}
		}
		return true;
	}
	
	/**
	 * Given a list of locations to replace, generates all possible FrankenTress that can
	 * be made by replacing each of those spots.
	 * 
	 * For instance, if you have template <br>
	 * 		A							  <br>
	 * 	   / \							  <br>
	 * 	  B   C							  <br>
	 * 
	 * So <code>nodeToIndex[A] = 0, nodeToIndex[B] = 1, nodeToIndex[C] = 2</code> <br>
	 * 
	 * Given anatomicalLocations = {false, true, true}, and given that B can be replaced by {D,E} and C can be replaced by {F,G},
	 * the output should be <br>
	 * 		A       A       A       A   <br>
	 * 	   / \     / \     / \     / \  <br>
	 *    D   F   D   G   E   F   E   G <br>
	 *    
	 * (Not necessarily in this order)
	 * 
	 * @param locations
	 * @return
	 */
	public Set<Tree> buildFrankenTreesAnatomically(ImmutableList<Set<Tree>> relevantLimbventory) {
		Preconditions.checkArgument(relevantLimbventory.size() == templateNodes.size());
		CartesianCollectionIterable<Tree> replacements = new CartesianCollectionIterable<Tree>(relevantLimbventory);
		
		Set<Tree> ret = Sets.newHashSet();
		if (replacements.size() > ITERATION_LIMIT) {
			System.out.println("ITERATION LIMIT EXCEEDED");
			return ret;
		}
		
		for (List<Tree> replacement : replacements)
			ret.add(buildFrankenTree(replacement));
		
		return ret;
	}
	
	/**
	 * Assumes this is a valid replacement
	 * 
	 * @param replacements
	 * @return
	 */
	public Tree buildFrankenTree(List<Tree> replacements) {
		Preconditions.checkArgument(replacements.size() == templateNodes.size());
		Tree frankenTree = template.deepCopy();
		List<Tree> frankenTreeList = frankenTree.preOrderNodeList();
		
		for (int i=0; i<templateNodes.size(); i++) {
			Tree toReplace = frankenTreeList.get(i);
			Tree replacement = replacements.get(i);
			// we aren't replacing anything
			if (replacement == null) continue;
			replaceLimb(toReplace, replacement, frankenTree);
		}
		
		return frankenTree;
	}
	
	/**
	 * Replaces a subtree of body.  Just returns replacement if the entire
	 * body is replaced.
	 * 
	 * @param toReplace
	 * @param replacement
	 * @param body
	 * @return
	 */
	private Tree replaceLimb(Tree toReplace, Tree replacement, Tree body) {
		Tree parent = toReplace.parent(body);
		if (parent == null)
			return replacement;
		for (int i=0; i<parent.getChildrenAsList().size(); i++)
			if (parent.getChild(i) == toReplace) {
				parent.setChild(i, replacement);
				break;
			}
		return body;
	}
	
	private Map<Tree,Integer> buildNodeToIndexMap() {
		Map<Tree,Integer> ret = Maps.newHashMap();
		for (int i=0; i<templateNodes.size(); i++)
			ret.put(templateNodes.get(i), i);
		return ret;
	}
	
	private List<Set<Tree>> buildEmptyLimbventory() {
		List<Set<Tree>> ret = Lists.newArrayList();
		// initialize each entry to empty set
		for (int i=0; i<templateNodes.size(); i++)
			ret.add(Sets.<Tree>newHashSet());
		return ret;
	}
	
	/**
	 * 
	 * @param substitutionBank
	 * @param fn
	 */
	public void buildLimbventory(Map<String, Set<Tree>> substitutionBank, ValidReplacementFn fn) {
		List<Set<Tree>> ret = Lists.newArrayList();
		// initialize each entry to empty set
		for (int i=0; i<templateNodes.size(); i++)
			ret.add(Sets.<Tree>newHashSet());
		
		for (Tree toReplace : templateNodes)
			for (String key : substitutionBank.keySet()) 
				for (Tree replacement : substitutionBank.get(key))
					if (fn.isValid(toReplace, replacement))
						append(toReplace, replacement, ret);
		limbventory = ret;
	}
	
	public interface ValidReplacementFn {
		public boolean isValid(Tree toReplace, Tree replacement);
	}
	
	
	/**
	 * Adds element to specified limbventory
	 * 
	 * @param key
	 * @param value
	 * @param limbventory
	 */
	public void append(Tree key, Tree value, List<Set<Tree>> limbventory) {
		if (!nodeToIndex.keySet().contains(key)) return;
		int index = nodeToIndex.get(key);
		Set<Tree> values = limbventory.get(index);
		values.add(value);
	}
	
	public void append(int index, Tree value, List<Set<Tree>> limbventory) {
		if (index < 0 || index >= templateNodes.size()) return;
		Set<Tree> values = limbventory.get(index);
		values.add(value);
	}
	
}
