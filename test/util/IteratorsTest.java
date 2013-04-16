package util;

import java.util.List;
import java.util.Set;

import org.junit.Test;

import util.Iterators.CartesianIterable;
import util.Iterators.CartesianIterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class IteratorsTest {

	
	@Test
	public void CartesianIterableTest() {
		ImmutableList<Iterable<Integer>> subspaces = ImmutableList.<Iterable<Integer>>builder()
				.add(Lists.newArrayList(0,1))
				.add(Lists.newArrayList(0,1))
				.build();	
				
		boolean visited[][] = { {false, false}, {false, false}};
		
		CartesianIterable<Integer> allPairs = new CartesianIterable<Integer> (subspaces);
		
		for (List<Integer>pair : allPairs) {
			visited[pair.get(0)][pair.get(1)] = true;
		}

		for (int i=0; i<2; i++)
			for (int j=0; j<2; j++)
				assertEquals(visited[i][j], true);
	
	}
	
	@Test
	public void CartesianIterableNullTest() {
		ImmutableList<Iterable<Integer>> subspaces = ImmutableList.<Iterable<Integer>>builder()
				.add(Lists.newArrayList(0,1))
				.add(Lists.<Integer>newArrayList())
				.add(Lists.newArrayList(0,1))
				.build();	
		

		Set<List<Integer>> seen = Sets.newHashSet();
		Set<List<Integer>> expected = Sets.newHashSet();
		
		for (int i=0; i<2; i++)
			for (int j=0; j<2; j++)
				expected.add(Lists.<Integer>newArrayList(i,null,j));
		
		CartesianIterable<Integer> allPairs = new CartesianIterable<Integer> (subspaces);
		
		for (List<Integer> pair: allPairs)
			seen.add(pair);
		
		assertEquals(seen.size(), expected.size());
		
		for (List<Integer> entry : seen) {
			assertTrue(expected.contains(entry));
		}
	}
	
	@Test
	public void CartesianIterableEmptyTest() {
		ImmutableList<Iterable<Integer>> subspaces = ImmutableList.<Iterable<Integer>>builder()
				.add(Lists.<Integer>newArrayList())
				.add(Lists.<Integer>newArrayList())
				.add(Lists.<Integer>newArrayList())
				.build();	
		
		
		CartesianIterator<Integer> allPairs = new CartesianIterator<Integer> (subspaces);
		assertTrue(!allPairs.hasNext());
		
	}
	
}
