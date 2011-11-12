/*
 * Copyright 2005 by X.
 * @author bsd
 */
package ca.ubc.cs.ferret.jdt.tests;

import java.util.Collection;

import junit.framework.TestCase;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import ca.ubc.cs.clustering.ClusteringPlugin;
import ca.ubc.cs.clustering.attrs.IAttributeSource;
import ca.ubc.cs.clustering.attrs.IClassifier;
import ca.ubc.cs.ferret.jdt.JavaModelHelper;
import ca.ubc.cs.ferret.jdt.attributes.MethodThrowsProvider;

public class JdtAttributeTests extends TestCase {

    IType javaLangString;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        JdtTests.createWorkspace();
        javaLangString = JavaModelHelper.getDefault().resolveType("java.lang.String"); 
        assertNotNull(javaLangString);
    }

    public void testTypeAttributes() {
        IAttributeSource ts = ClusteringPlugin.getDefault().getAttributeSourceManager().getAttributeSource(javaLangString);
        assertFalse(ts.getAttributeNames().size() == 0);
        for(String name : ts.getAttributeNames()) {
            assertTrue(ts.getAttributeDomain(name) == null || ts.getAttributeDomain(name).size() > 0);
            assertTrue(ts.getAttribute(name, javaLangString) != null 
            		|| name.equals("ca.ubc.cs.ferret.jdt.classifier.MemberDeclaringType"));
        }
    }

    public void testMethodAttributes() {
        IAttributeSource ts;
        try {
        	Object obj = javaLangString.getMethods()[0];
            ts = ClusteringPlugin.getDefault().getAttributeSourceManager().getAttributeSource(obj);
            assertFalse(ts.getAttributeNames().size()== 0);
            for(String name : ts.getAttributeNames()) {
                assertTrue(ts.getAttributeDomain(name) == null || ts.getAttributeDomain(name).size() > 0);
                assertNotNull(ts.getAttribute(name, obj));
            }
        } catch (JavaModelException e) {
            fail("Unexpected JavaModelException: " + e);
        }
    }
    
    public void testFieldAttributes() {
        IAttributeSource ts;
        try {
        	Object obj = javaLangString.getFields()[0];
            ts = ClusteringPlugin.getDefault().getAttributeSourceManager().getAttributeSource(obj);
            assertFalse(ts.getAttributeNames().size() == 0);
            for(String name : ts.getAttributeNames()) {
                assertTrue(ts.getAttributeDomain(name) == null || ts.getAttributeDomain(name).size() > 0);
                assertNotNull(ts.getAttribute(name, obj));
            }
        } catch (JavaModelException e) {
            fail("Unexpected JavaModelException");
        }
    }

    public void testClassFileAttributes() {
    	Object obj = javaLangString.getClassFile();
        IAttributeSource ts = ClusteringPlugin.getDefault().getAttributeSourceManager().getAttributeSource(obj);
        assertFalse(ts.getAttributeNames().size() == 0);
        for(String name : ts.getAttributeNames()) {
            assertTrue(ts.getAttributeDomain(name) == null || ts.getAttributeDomain(name).size() > 0);
            assertNotNull(ts.getAttribute(name, obj));
        }
    }

    public void testMethodThrowsAttributeProvider() {
    	try {
    		IMethod throwsMethod = null;
    		for(IMethod m : javaLangString.getMethods()) {
    			if(m.getExceptionTypes().length > 0) {
    				throwsMethod = m;
    				break;
    			}
    		}
    		assertNotNull(throwsMethod);
    		assertTrue(throwsMethod.exists());
    		IClassifier<IMethod,Object> av = new MethodThrowsProvider();
    		Object value = av.getCategory(throwsMethod);
    		assertNotNull(value);
    		assertTrue(value instanceof Collection);
			assertTrue(((Collection<?>)value).size() == 1);
			Object ex = ((Collection<?>)value).iterator().next();
    		assertTrue(ex instanceof IType);
    		assertEquals("java.io.UnsupportedEncodingException", 
    				((IType)ex).getFullyQualifiedName());
    	} catch(JavaModelException e) {
    		fail("JavaModelException: " + e);
    	}
    }

    
    @Override
    protected void tearDown() throws Exception {
        javaLangString = null;
        JdtTests.destroyWorkspace();
        super.tearDown();
    }
    
    
}
