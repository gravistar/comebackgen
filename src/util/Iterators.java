package util;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Stack;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
/**
 * Contains some useful utility iterators.
 * 
 * @author david
 *
 */
public class Iterators {
	
	public static class PowerSetIterable<T> implements Iterable<List<T>> {
		private ImmutableList<T> set;
		
		public PowerSetIterable(ImmutableList<T> set) {
			this.set = set;
		}
		
		@Override
		public PowerSetIterator<T> iterator() {
			return new PowerSetIterator<T>(set);
		}
		
		public int size() {
			return (1 << set.size());
		}
	}
	
	
	// TODO: make this easier to set set size limits (don't use bitmask)
	public static class PowerSetIterator<T> implements Iterator<List<T>> {
		private ImmutableList<T> set;
		private int mask;  // should not have to use powerset on anything bigger than this
		
		public PowerSetIterator (ImmutableList<T> set){
			this.set = set;
			this.mask = 0;
		}
		
		@Override
		public boolean hasNext() {
			return mask == (1 << set.size());
		}

		@Override
		public List<T> next() {
			
			List<T> ret = Lists.newArrayList();
			
			for (int i=0; i<set.size(); i++) {
				if ( (mask & (1 << set.size())) != 0)
					ret.add(set.get(i));
				else
					ret.add(null);
			}
			
			
			// increment mask
			mask++;
			return ret;
		}

		@Override
		public void remove() {
			// unsupported
			
		}
	}
	
	
	/**
	 * Makes it usable in "foreach" loops
	 */
	public static class CartesianIterable<T> implements Iterable<List<T>>{
		private final ImmutableList<Iterable<T>> subspaces;
		public CartesianIterable (ImmutableList<Iterable<T>> subspaces) {
			this.subspaces = subspaces;
		}
		 
		@Override
		public Iterator<List<T>> iterator() {
			return new CartesianIterator<T>(subspaces);
		}
		
	}
	
	/**
	 * Makes it usable in "foreach" loops
	 */
	public static class CartesianCollectionIterable<T> implements Iterable<List<T>>{
		private long size = Long.MIN_VALUE;
		private final ImmutableList<? extends Collection<T>> subspaces;
		public CartesianCollectionIterable (ImmutableList<? extends Collection<T>> subspaces) {
			this.subspaces = subspaces;
		}
		 
		@Override
		public Iterator<List<T>> iterator() {
			return new CartesianCollectionIterator<T>(subspaces);
		}
		
		public long size() {
			if (size == Integer.MIN_VALUE)
				size = new CartesianCollectionIterator<T>(subspaces).size();
			return size;
		}
		
	}
	
	/**
	 * An iterator over the Cartesian product of an ordered set of spaces.
	 * 
	 * Edge cases:
	 * <ul>
	 * <li> subspaces contain empty iterators (if you call hasNext() on one, it immediately is false)
	 * <li> subspaces itself is empty
	 * </ul>
	 * 
	 * 
	 * @author david
	 *
	 * @param <T>
	 */
	public static class CartesianIterator<T> implements Iterator<List<T>>{

		private ImmutableList<? extends Iterable<T>> subspaces;
		private Stack<Iterator<T>> iteratorStack;
		private Stack<T> currentPoint;

		public CartesianIterator(ImmutableList<? extends Iterable<T>> subspaces) {
			this.subspaces = subspaces;
			this.iteratorStack = new Stack<Iterator<T>>();
			this.currentPoint = new Stack<T>();
			prepareFields();
		}

		/**
		 * Prepares the <code>iteratorStack</code> and <code>currentPoint</code> fields
		 * for iteration.
		 */
		private void prepareFields() {
			for (int i=0; i<subspaces.size(); i++) {
				Iterator<T> it = subspaces.get(i).iterator();

				iteratorStack.add(it);
				currentPoint.add(null); // gross but can't be helped

				if (it.hasNext())
					break;
			}
		}

		/**
		 * 
		 */
		@Override
		public boolean hasNext() {

			// pop off the iterators which are done until we hit one which isn't
			while (iteratorStack.size() > 0) {
				Iterator<T> head = iteratorStack.peek();
				if (head.hasNext())
					break;
				iteratorStack.pop();
				currentPoint.pop();
			}

			return iteratorStack.size() > 0;
		}


		/**
		 * Returns a list which represents a point in the Cartesian product of the subspaces.
		 * Coordinates of empty subspaces have null (not much we can do about that). 
		 */
		@Override
		public List<T> next() {

			if (iteratorStack.size() == 0)
				throw new NoSuchElementException();

			// remove old element from this subspace and add the next
			currentPoint.pop();
			currentPoint.add(iteratorStack.peek().next());

			// add iterators until we reach the last subspace
			while (iteratorStack.size() < subspaces.size()) {
				iteratorStack.add(subspaces.get(iteratorStack.size()).iterator());
				if (iteratorStack.peek().hasNext())
					currentPoint.add(iteratorStack.peek().next());
				else
					currentPoint.add(null);
			}

			return Lists.newArrayList(currentPoint);
		}

		@Override
		public void remove() {
			// unsupported
		}
	}
	
	public static class CartesianCollectionIterator<T> implements Iterator<List<T>> {
		private CartesianIterator<T> iterator;
		private ImmutableList<? extends Collection<T>> subspacesAsCollections;

		public CartesianCollectionIterator (ImmutableList<? extends Collection<T>> subspaces) {
			this.iterator = new CartesianIterator<T>(subspaces);
			this.subspacesAsCollections = subspaces;
		}

		@Override
		public boolean hasNext() { return iterator.hasNext(); }

		@Override
		public List<T> next() { return iterator.next(); }

		@Override
		public void remove() {
			// unsupported
		}

		// returns size of cartesian space
		public long size() {
			long size = 1;
			for (int i=0; i<subspacesAsCollections.size(); i++)
				size *= subspacesAsCollections.get(i).size();
			return size;
		}

	}

}
