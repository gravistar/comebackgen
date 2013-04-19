package comeback;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.StringTokenizer;

import util.Crawler;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;

class ComebackGenerator {

	private static final String BANK_PATH = "bank/";
	private static final String DATA_PATH = "data/";
	private static final String DATA_INPUT_PATH = DATA_PATH + "input/";
	private static final String DATA_OUTPUT_PATH = DATA_PATH + "output/";
	private static final String MODELS_PATH = "lib/edu/stanford/nlp/models/lexparser/";
	private static final String ENGLISH_PCFG_PATH = MODELS_PATH + "englishPCFG.ser.gz";

	private static final LexicalizedParser lp = LexicalizedParser.loadModel(ENGLISH_PCFG_PATH); 
	static {
		lp.setOptionFlags(new String[]{"-maxLength", "80", "-retainTmpSubcategories"});
	}
	
	private static final TreebankLanguagePack tlp = new PennTreebankLanguagePack();
	private static final GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
	
	
	public interface SentenceFn<T> {
		public T apply(String sentence);
		public T apply(String sentence, PrintWriter out);
	}
	
	
	public static <T>void processFile(File input, SentenceFn<T> callback) throws IOException {
		
		BufferedReader in = new BufferedReader(new FileReader(input));
		StringTokenizer inputTok = new StringTokenizer(input.getPath(), "/");
		String tail = "dumb";
		while(inputTok.hasMoreTokens()) {
			tail = inputTok.nextToken();
		}
		String outputFileName = DATA_OUTPUT_PATH + tail;
		PrintWriter out = new PrintWriter(new FileWriter(outputFileName));
		
		
		String line,tagged;
		while ((line = in.readLine()) != null) {
			parseSentence(line, out, callback);
		}
		out.close();
		in.close();
	}
	
	
	/**
	 * This makes the 
	 * @param sentence
	 */
	public static <T> T parseSentence(String sentence, PrintWriter out, SentenceFn<T> callback) {
		return callback.apply(sentence, out);
	}
	
	private static Set<String> verbs = Sets.newHashSet(Arrays.asList("VP","VB", "VBG", "VBD"));
	private static Set<String> nouns = Sets.newHashSet(Arrays.asList("NP","NNS", "NN"));
	private static Set<String> restricted = Sets.newHashSet(Arrays.asList("You", "you", "Your", "your", "I", "my"));
	private static Map<String, Set<Tree>> substitutionBank; // key: either a tagged value or CFG value
														   // value: a list of trees from the bank who have the key as their root label
	
	private static Map<String, Set<Tree>> buildSubstitutionBank() throws IOException {
		Map<String, Set<Tree>> bank = Maps.newHashMap();
		File bankFile = new File(DATA_PATH + BANK_PATH + "cards-small");
		BufferedReader in = new BufferedReader(new FileReader(bankFile));
		String line;
		while ((line = in.readLine()) != null){
			Tree parse = lp.apply(line);	
			Map<String, Set<Tree>> toAdd = Maps.newHashMap();
		
			// Flatten away ROOT and FRAGs
			while(parse.value().equals("ROOT") || parse.value().equals("FRAG")){
				parse = parse.firstChild();
			}
			
			// Take out S
			if (parse.value().equals("S")) {
				for (Tree child : parse.children()){
					Set<Tree> toPut = Sets.newHashSet();
					if (toAdd.keySet().contains(child.value()))
						toPut.addAll(toAdd.get(child.value()));
					toPut.add(child);
					toAdd.put(child.value(), toPut);
				}
			} else {
				Set<Tree> toPut = Sets.newHashSet();
				if (toAdd.keySet().contains(parse.value()))
					toPut.addAll(toAdd.get(parse.value()));
				toPut.add(parse);
				toAdd.put(parse.value(), toPut);
			}
			
			for (String key : toAdd.keySet()) {
				Set<Tree> toPut = Sets.newHashSet();
				if (bank.keySet().contains(key))
					toPut.addAll(bank.get(key));
				toPut.addAll(toAdd.get(key));
				bank.put(key, toPut);
			}
			int k = 1;
		}
		return bank;
	}
	
	/**
	 * Get all the possible replacements for a given set of parse trees given the
	 * substitution bank.
	 * @return
	 */	
	private static Set<Tree> doSubstitutionOnTrees(List<Tree> roots, Set<Tree> substitutions) {
		Set<SubstitutedTree> substitutedTrees = Sets.newHashSet();
		for (Tree root : roots) {
			substitutedTrees.addAll(doSubstitutionsOnTree(root, substitutions));
		}
		Set<Tree> ret = Sets.newHashSet();
		for (SubstitutedTree substitutedTree : substitutedTrees) {
			ret.add(substitutedTree.tree);
		}
		return ret;
	}
	
	
	private static Set<SubstitutedTree> doSubstitutionsOnTree(Tree root, Set<Tree> substitutions) {
		Set<SubstitutedTree> substitutedTrees = Sets.newHashSet();
		Queue<SubstitutedTree> queue = Lists.<SubstitutedTree>newLinkedList();
		
		SubstitutedTree init = new SubstitutedTree(root, Sets.<Tree>newHashSet());
		
		queue.add(init);
		// should terminate because we disallow nested substitutions
		while (!queue.isEmpty()) {
			SubstitutedTree cur = queue.remove();
			System.out.println("Cur : " + cur + " Queue size: " + queue.size());
			
			for (Tree substitution : substitutions) {
				Set<SubstitutedTree> possibleSubstitutions = doSubstitutionOnTree(cur, substitution);
				substitutedTrees.addAll(possibleSubstitutions);
				queue.addAll(possibleSubstitutions);
			}
		}
		
		return substitutedTrees;
	}
	
//	private static class FrankenTreeFactory {
//		public Tree template;
//		public Map<Tree, Set<Tree>> limbventory = Maps.newHashMap(); // bidirectional map with these possibilities:
//																	 // key: body position, value: limbs that can be put there
//																	 // key: limb, value: positions where it can be put
//		
//		
//		public FrankenTreeFactory (Tree template) {
//			this.template = template;
//		}
//		
//		public void append(Tree key, Tree value) {
//			Set<Tree> values = Sets.newHashSet();
//			if (limbventory.keySet().contains(key))
//				values.addAll(limbventory.get(key));
//			limbventory.put(key, values);
//		}
//		
//		/**
//		 * 
//		 * @param replacementMap
//		 * 		bidirectional map between nodes in template and limbs
//		 * @return
//		 * 		a new frankentree with the correct limbs
//		 */
//		public Tree buildFrankenTree(Map<Tree,Tree> replacementMap) {
//			Tree frankenTree = template.deepCopy();
//			Map<Tree,Tree> frankenMap = Maps.newHashMap();
//			
//			for (int i=0; i<template.preOrderNodeList().size(); i++){
//				frankenMap.put(frankenTree.getChild(i), template.getChild(i));
//				frankenMap.put(template.getChild(i), frankenTree.getChild(i));
//			}
//			
//			for (int i=0; i<template.preOrderNodeList().size(); i++) {
//				Tree templateLimb = template.getChild(i); 
//				Tree toReplace = frankenMap.get(templateLimb);
//				// This node is unreachable now because some other limb must
//				// have replaced an ancestor
//				if (toReplace.nodeNumber(frankenTree) == -1)
//					continue;
//				Tree parent = toReplace.parent(frankenTree);
//
//				// The whole root is being replaced
//				if (parent == null) {
//					frankenTree = replacementMap.get(templateLimb);
//					break;
//				}
//				
//				for (int ci = 0; ci < parent.getChildrenAsList().size(); ci++) {
//					if (parent.getChild(ci) == toReplace) {
//						parent.setChild(ci, replacementMap.get(templateLimb));
//					}
//				}
//			}
//			return frankenTree;
//		}
//	}
	
	
	/**
	 * Generates all the trees which can be made by replacing any subtree <code>T</code> of <code>root</code>
	 * with <code>substitution</code> under the following conditions:
	 * <ul>
	 * <li> <code>T</code> is not a subtree of any substitution made before in <code>root</code>. This is to prevent nested substitutions.
	 * <li> Replacing <code>T</code> with <code>substitution</code> is valid based on their structure
	 * </ul>
	 * 
	 * @param root
	 * 		The tree to apply substitutions on (may already have previous substitutions)
	 * @param substitution
	 * 		The substitution to apply
	 * @return
	 */
	private static Set<SubstitutedTree> doSubstitutionOnTree(SubstitutedTree root, Tree substitution) {
		Set<SubstitutedTree> substitutedTrees = Sets.newHashSet();
		
		List<Tree> nodes = root.nodes();
		
		int index = 0;
		for (Tree node : nodes) {
			if (!substitution.value().equals("NP") && !verbs.contains(substitution.value())) continue;
			
			boolean partOfExistingSub = false; // True if this node is a subtree of an existing substitution in Root
			for (Tree prevSubstitution : root.substitutions) {
				if (node.nodeNumber(prevSubstitution) != -1) {
					partOfExistingSub = true;
					break;
				}
			}
			if (partOfExistingSub)
				continue;
			
			if (isValidReplacement(node, substitution)) {
				
				Tree deepcopy = root.tree.deepCopy();
				List<Tree> copiedNodes = deepcopy.preOrderNodeList();
				Tree toReplace = copiedNodes.get(index);
				Tree parent = toReplace.parent(deepcopy);
				
				// we can replace the whole root
				if (parent == null) {
					substitutedTrees.add(new SubstitutedTree(substitution, root.substitutions));
					continue;
				}
				
				int ci;
				for (ci = 0; ci < parent.children().length; ci++) {
					// find matching child index and replace it
					if (parent.children()[ci] == toReplace) {
						parent.setChild(ci, substitution);
						break;
					}
				}
				
				Set<Tree> substitutionsOfCopy = Sets.newHashSet(root.substitutions);
				substitutionsOfCopy.add(substitution);
				substitutedTrees.add(new SubstitutedTree(deepcopy, substitutionsOfCopy));	
			}
			// gross, shouldn't have to do this nonsense...
			index++;
		}
		return substitutedTrees;
	}
	
	
	/**
	 * A Tree which has a set its subtrees replaced by trees from the substitution bank.
	 * It also has easy references to its substitutions.
	 * 
	 * Also, the substitutions are shared among SubstitutedTrees. (Ie. You can have
	 * trees A and B which have a common substitution C as a subtrees).
	 * 
	 * @author david
	 *
	 */
	private static class SubstitutedTree {
		public final Tree tree;
		public final Set<Tree> substitutions;
		public SubstitutedTree(Tree tree, Set<Tree> substitutions) {
			this.tree = tree;
			this.substitutions = substitutions;
		}
		public List<Tree> nodes() {
			return tree.preOrderNodeList();
		}
		
		@Override
		public String toString() {
			return tree.toString() + " with " + substitutions.size() + " substitutions";
		}
		
	}	
	
	/**
	 * Right now this just uses the following dumb rule:
	 * <ul>
	 * <li>The replacement tree must have the same structure as the tree being replaced.
	 * <li>If two nodes in the same position are not leaves, they must have the same value UNLESS
	 * 		they are verbs. If they are both verbs, then they just need to both be verbs.
	 * 	   It's very questionable if this is too restrictive.
	 * <li>If two nodes in the same position are leaves, they must have the same value if
	 * 	   they both have a restricted value.
	 * </ul> 
	 * @param toReplace
	 * @param replacement
	 * @return
	 */
	private static boolean isValidReplacement(Tree toReplace, Tree replacement) {
		if (toReplace.value().equals(replacement.value())) {
		
			if (toReplace.value().equals("VP") || toReplace.value().equals("NP")) {
				if (!toReplace.firstChild().value().equals(replacement.firstChild().value()))
					return false;
			}
			
			boolean usesRestricted = false;
			
			// check to see if it doesn't contain restricted words
			List<Word> toReplaceWords = toReplace.yieldWords();
			for (Word word : toReplaceWords) {
				if (restricted.contains(word.value()))
					usesRestricted = true;
			}
			
			List<Word> replacementWords = replacement.yieldWords();
			for (Word word : replacementWords) {
				if (restricted.contains(word.value()))
					usesRestricted = true;
			}
			
			return !usesRestricted;	
		}
		return false;
	}
	
	public interface TreeFn<T> {
		public T apply(Tree node);
	}
	
	/**
	 * Applies the specified function on the tree in BFS order
	 * @param root
	 * @param applyFn
	 */
	private static <T> void applyOnTree(Tree root, TreeFn<T> applyFn) {
		Set<Tree> seen = Sets.newHashSet();
    	Queue<Tree> q = new LinkedList<Tree>();
    	q.add(root);
		while (!q.isEmpty()) {
			Tree curNode = q.remove();
			applyFn.apply(curNode);
			for (Tree child : curNode.getChildrenAsList()){
				if (!seen.contains(child)){
					q.add(child);
					seen.add(child);
				}
			}
		}
	}
	
	
	private static void exploreTree(Tree root, PrintWriter out) {
		out.write("STARTING TREE EXPLORATION\n");
		Set<Tree> seen = Sets.newHashSet();
    	Queue<Tree> q = new LinkedList<Tree>();
    	q.add(root);
    	int i = 0;
		while (!q.isEmpty()) {
			Tree curNode = q.remove();
			out.write("Node " + Integer.toString(i) + " has label " + curNode.label().toString() + " Phrasal? " + curNode.isPhrasal() + " Terminal? " + curNode.isLeaf() + "\n");
			i++;
			for (Tree child : curNode.getChildrenAsList()){
				if (!seen.contains(child)){
					q.add(child);
					seen.add(child);
				}
			}
		}
		out.write("DONE WITH TREE EXPLORATION\n");
	}
	
	public static void main(String[] args) {
		File startingDir = new File(DATA_INPUT_PATH);
		List<File> files = Crawler.getFilesInDir(startingDir);
		
		// try building bank
		try {
			substitutionBank = buildSubstitutionBank();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		for (File file : files) {
			try {
				processFile(file, new SentenceFn<Void>() {

					@Override
					public Void apply(String sentence) {
						return null;
					}

					@Override
					public Void apply(String sentence, PrintWriter out) {
						// run this on the string itself!! this is better anyway
						Tree parse = lp.apply(sentence);
						
						System.out.println("Creating comebacks for " + "\"" + sentence + "\"");
						FrankenTreeFactory factory = new FrankenTreeFactory(parse);
						
						factory.buildLimbventory(substitutionBank, new FrankenTreeFactory.ValidReplacementFn() {
							
							@Override
							public boolean isValid(Tree toReplace, Tree replacement) {
								return isValidReplacement(toReplace, replacement);
							}
						});
						
						Set<Tree> comebacks = factory.generateAllFrankenTrees();
						
						System.out.println("Number of comebacks generated: " + comebacks.size());
						System.out.println();
						
						for (Tree comeback : comebacks) {
							out.write("Comeback: " + comeback.yieldWords() + "\n");
							out.write("Original Tree: " + parse + "\n");
							out.write("Comeback Tree: " + comeback + "\n\n");
						}
						
//						factory.buildLimbventory(substitutionBank);
						
//						factory.
//						
//						
//						
//						
//						List<Tree> roots = Lists.newArrayList();
//						roots.add(parse);
//						
//						
//						
//						for (String key : substitutionBank.keySet()) {
//							Set<Tree> substitutions = substitutionBank.get(key);
//							
//							for (Tree substitution : substitutions) {
//								out.write("Substitution Tree: " + substitution + "\n"); 
//							}
//							
//							FrankenTreeFactory factory = new FrankenTreeFactory(template)
//							Set<Tree> comebacks = ;
//							
//							Set<Tree> comebacks = doSubstitutionOnTrees(roots, substitutions);
//							for (Tree comeback : comebacks) {
//								out.write("Comeback: " + comeback.yieldWords() + "\n");
//								out.write("Original Tree: " + roots.get(0) + "\n");
//								out.write("Comeback Tree: " + comeback + "\n\n");
//								
//							}
//							System.out.format("root: %s key: %s num comebacks: %d\n", sentence, key, comebacks.size());
//						}
						
//						exploreTree(parse, out);
//						
//						parse.pennPrint(out);
//						out.write("\n");
//						
//						GrammaticalStructure gs = gsf.newGrammaticalStructure(parse);
//						List<TypedDependency> tdl = gs.typedDependenciesCCprocessed();
//						out.write(tdl.toString());
//						
//						TreePrint tp = new TreePrint("penn,typedDependenciesCollapsed");
//						tp.printTree(parse, out);
//						return parse;
						
						return null;
					}
				});
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		//LexicalizedParser lp = LexicalizedParser.loadModel(ENGLISH_PCFG_PATH); //<--TODO path to grammar goes here
		
	}

}