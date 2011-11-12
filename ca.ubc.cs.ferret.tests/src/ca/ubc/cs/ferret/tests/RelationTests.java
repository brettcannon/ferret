package ca.ubc.cs.ferret.tests;

import java.util.Collection;
import java.util.HashSet;
import java.util.NoSuchElementException;

import junit.framework.TestCase;

import org.apache.commons.collections15.Transformer;
import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.NullProgressMonitor;

import ca.ubc.cs.ferret.FerretFatalError;
import ca.ubc.cs.ferret.model.DifferencingSphereCompositor;
import ca.ubc.cs.ferret.model.DisjunctingSphereCompositor;
import ca.ubc.cs.ferret.model.ErrorRaisingSphere;
import ca.ubc.cs.ferret.model.IRelation;
import ca.ubc.cs.ferret.model.IRelationFactory;
import ca.ubc.cs.ferret.model.ISphere;
import ca.ubc.cs.ferret.model.IntersectingSphereCompositor;
import ca.ubc.cs.ferret.model.JoinRelation;
import ca.ubc.cs.ferret.model.NameAlreadyRegisteredException;
import ca.ubc.cs.ferret.model.NamedJoinRelation;
import ca.ubc.cs.ferret.model.NamedRelation;
import ca.ubc.cs.ferret.model.NullRelation;
import ca.ubc.cs.ferret.model.Sphere;
import ca.ubc.cs.ferret.model.ReplacementSphereCompositor;
import ca.ubc.cs.ferret.model.TransformingSphereCompositor;
import ca.ubc.cs.ferret.model.UnioningSphereCompositor;
import ca.ubc.cs.ferret.types.FerretObject;

public class RelationTests extends TestCase {

	public void testBasicRelation() {
		Object o = new Object();
		ISphere tb = new Sphere("testBasicRelation()");
		IRelationFactory opf = new UniversalRelation();
		IRelation op = opf.configure(new NullProgressMonitor(), tb.createResolverState(null), o);
		assertTrue(op.hasNext());
		assertEquals(o, op.next(Object.class));
		assertFalse(op.hasNext());
		assertNull(op.next());
	}
	
	public void testSphere() {
		Object o = new Object();
		Sphere tb = new Sphere("testSphere()");
		tb.register("pass-through", new UniversalRelation());
		
		try {
			tb.resolve(new NullProgressMonitor(), "non-existant-relation", o);
			fail("Exception should have been raised");
		} catch(UnsupportedOperationException e) { /* do nothing */ }
		
		IRelation op = tb.resolve(new NullProgressMonitor(), "pass-through", o);
		assertTrue(op.hasNext());
		assertEquals(o, op.next(Object.class));
		assertFalse(op.hasNext());
		assertNull(op.next());
	}
	
	public void testJoinRelation() {
		Sphere tb = new Sphere("testJoinRelation()");
		tb.register("pass-through", new UniversalRelation());
		tb.register("collection", new CollectionIterationRelation());
		tb.register("test", new NamedJoinRelation("collection", "pass-through"));

		IRelation op = tb.resolve(new NullProgressMonitor(), "test",
				1, 2, 3, 4, 5);
		assertTrue(op.hasNext());
		assertEquals(1, (int)op.next(Integer.class));
		assertTrue(op.hasNext());
		assertEquals(2, (int)op.next(Integer.class));
		assertTrue(op.hasNext());
		assertEquals(3, (int)op.next(Integer.class));
		assertTrue(op.hasNext());
		assertEquals(4, (int)op.next(Integer.class));
		assertTrue(op.hasNext());
		assertEquals(5, (int)op.next(Integer.class));
		assertFalse(op.hasNext());
		try {
			op.next();
			fail("Expected NoSuchElementException");
		} catch(NoSuchElementException e) {/* success */}
	}
	
	public void testPathalogicalJoinRelation() {
		Sphere tb = new Sphere("testPathalogicalJoinRelation()");
		tb.register("sink", new NullRelation());
		tb.register("collection", new CollectionIterationRelation());
		tb.register("test", new NamedJoinRelation("collection", "sink"));

		IRelation op = tb.resolve(new NullProgressMonitor(), "test",
				1, 2, 3, 4, 5);
		assertFalse(op.hasNext());
		try {
			op.next();
			fail("Expected NoSuchElementException");
		} catch(NoSuchElementException e) {/* success */}
	}
	
	public void testSimpleUnionSphere() {
		UnioningSphereCompositor tb = new UnioningSphereCompositor();
		Sphere subtb = new Sphere("simple");
		subtb.register("foo", new UniversalRelation());
		tb.add(subtb);
		IRelation op = tb.resolve(new NullProgressMonitor(), "foo", "bar");
		Collection<String> result = op.asCollection(String.class);
		assertEquals(1, result.size());
		assertEquals("bar", result.iterator().next());

		tb.add(subtb);
		op = tb.resolve(new NullProgressMonitor(), "foo", "bar");
		result = op.asCollection(String.class);
		assertEquals(2, result.size());
		assertEquals(1, new HashSet<String>(result).size());
		assertEquals("bar", result.iterator().next());

		tb.add(subtb);
		op = tb.resolve(new NullProgressMonitor(), "foo", "bar");
		result = op.asCollection(String.class);
		assertEquals(3, result.size());
		assertEquals(1, new HashSet<String>(result).size());
		assertEquals("bar", result.iterator().next());
	}
	
	public void testSinkUnionSphere() {
		UnioningSphereCompositor tb = new UnioningSphereCompositor();
		Sphere subtb = new Sphere("sink");
		subtb.register("foo", new NullRelation());
		tb.add(subtb);
		tb.add(subtb);
		tb.add(subtb);
		IRelation op = tb.resolve(new NullProgressMonitor(), "foo", new Object());
		assertFalse(op.hasNext());
	}

	public void testComplexUnionSphere() {
		UnioningSphereCompositor tb = new UnioningSphereCompositor();
		Sphere subtb = new Sphere("simple");
		subtb.register("foo", new UniversalRelation());
		tb.add(subtb);
	
		subtb = new Sphere("simple");
		subtb.register("foo", new TransformingRelation<String>(String.class, new Transformer<String, String>() {
			public String transform(String input) {
				return StringUtils.reverse(input);
			}}));
		tb.add(subtb);
		
		IRelation op = tb.resolve(new NullProgressMonitor(), "foo", "bar");
		Collection<String> result = op.asCollection(String.class);
		assertEquals(2, result.size());
		assertTrue(result.contains("bar"));
		assertTrue(result.contains("rab"));
	}
	
	public void testIntersectingSphere() {
		IntersectingSphereCompositor tb = new IntersectingSphereCompositor();
		Sphere subtb = new Sphere("simple");
		subtb.register("foo", new UniversalRelation());
		tb.add(subtb);
		IRelation op = tb.resolve(new NullProgressMonitor(), "foo", "bar");
		Collection<String> result = op.asCollection(String.class);
		assertEquals(1, result.size());
		assertEquals("bar", result.iterator().next());

		tb.add(subtb);
		op = tb.resolve(new NullProgressMonitor(), "foo", "bar");
		result = op.asCollection(String.class);
		assertEquals(1, result.size());
		assertEquals("bar", result.iterator().next());

		subtb = new Sphere("simple");
		subtb.register("foo", new TransformingRelation<String>(String.class, new Transformer<String, String>() {
			public String transform(String input) {
				return StringUtils.reverse(input);
			}}));
		tb.add(subtb);
		op = tb.resolve(new NullProgressMonitor(), "foo", "bar");
		assertFalse(op.hasNext());
	}

	public void testSimpleDisjunctingCompositor() {
		DisjunctingSphereCompositor tb = new DisjunctingSphereCompositor ();
		Sphere subtb = new Sphere("simple");
		subtb.register("foo", new UniversalRelation());
		tb.add(subtb);
		tb.add(subtb);
		IRelation op = tb.resolve(new NullProgressMonitor(), "foo", "bar");
		assertFalse(op.hasNext());
	}

	public void testComplicatedDisjunctingCompositor() {
		DisjunctingSphereCompositor tb = new DisjunctingSphereCompositor ();
		Sphere subtb = new Sphere("simple");
		subtb.register("foo", new FixedRelation(tb, "a", "b", "c"));
		tb.add(subtb);
		
		subtb = new Sphere("simple");
		subtb.register("foo", new FixedRelation(tb, "b", "d", "e"));
		tb.add(subtb);
		
		subtb = new Sphere("simple");
		subtb.register("foo", new FixedRelation(tb, "c", "d", "f"));
		tb.add(subtb);
		
		IRelation op = tb.resolve(new NullProgressMonitor(), "foo", "bar");
		Collection<String> result = op.asCollection(String.class);
		assertEquals(3, result.size());
		assertTrue(result.contains("a"));
		assertTrue(result.contains("e"));
		assertTrue(result.contains("f"));
	}

	public void testDifferenceCompositor() {
		DifferencingSphereCompositor tb = new DifferencingSphereCompositor ();
		Sphere subtb = new Sphere("simple");
		subtb.register("foo", new FixedRelation(tb, "a", "b", "c", "d", "e"));
		tb.add(subtb);
		
		subtb = new Sphere("simple");
		subtb.register("foo", new FixedRelation(tb, "b", "d"));
		tb.add(subtb);
		
		subtb = new Sphere("simple");
		subtb.register("foo", new FixedRelation(tb, "c", "f"));
		tb.add(subtb);
		
		IRelation op = tb.resolve(new NullProgressMonitor(), "foo", "bar");
		Collection<String> result = op.asCollection(String.class);
		assertEquals(2, result.size());
		assertTrue(result.contains("a"));
		assertTrue(result.contains("e"));
		assertFalse(result.contains("b"));
		assertFalse(result.contains("f"));
	}

	public void testRestrictingCompositor() {
		UnioningSphereCompositor union = new UnioningSphereCompositor();
		ReplacementSphereCompositor replacement = new ReplacementSphereCompositor();
		// first add an error-raising tb so anything percolating back up to the
		// union wil barf
		union.add(new ErrorRaisingSphere("in union"));
		union.add(replacement);
		
		Sphere tb = new Sphere("Smiling");
		tb.register("smile", new UniversalRelation());
		replacement.add(tb);
		
		tb = new Sphere("Frowning");
		tb.register("alias", new NamedRelation("smile"));	
		tb.register("frown", new CollectionIterationRelation());
		TransformingSphereCompositor transforming = new TransformingSphereCompositor("restricting", tb);
		transforming.addTransform(new TransformingRelation<Integer>(
				Integer.class, new Transformer<Integer, String>() {
					public String transform(Integer input) {
						return input.toString();
					}
				}));
		replacement.add(transforming);		
		
		IRelation op = transforming.createResolverState(null).process(new NullProgressMonitor(), "frown", 
				new FerretObject(1, transforming));
		assertTrue(op.hasNext());
		assertTrue(op.next().getPrimaryObject() instanceof String);

		// verify that it is permissive
		op = transforming.createResolverState(null).process(new NullProgressMonitor(), "frown", 
				new FerretObject(new HashSet(), transforming));
		assertTrue(op.hasNext());
		assertTrue(op.next().getPrimaryObject() instanceof HashSet);

		op = replacement.createResolverState(null).process(new NullProgressMonitor(), "smile", 
				new FerretObject(1, transforming));
		assertTrue(op.hasNext());
		assertTrue(op.next().getPrimaryObject() instanceof Integer);

		try {
			op = transforming.resolve(new NullProgressMonitor(), "alias", 
					new FerretObject(1, transforming));
			fail("should have gotten to an " + ErrorRaisingSphere.class.getName());
		} catch(FerretFatalError e) {
			// this is supposed to happen
		}
	}
}
