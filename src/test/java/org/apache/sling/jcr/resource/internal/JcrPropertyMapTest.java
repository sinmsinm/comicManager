/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.jcr.resource.internal;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.testing.jcr.RepositoryTestBase;
import org.apache.sling.jcr.resource.JcrPropertyMap;

public class JcrPropertyMapTest extends RepositoryTestBase {

    private static final String PROP_NAME = "prop_name";

    private static final String PROP_NAME_NIL = "prop_name_nil";

    private String rootPath;

    private Node rootNode;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        rootPath = "/test" + System.currentTimeMillis();
        rootNode = getSession().getRootNode().addNode(rootPath.substring(1),
            "nt:unstructured");
        session.save();
    }

    @Override
    protected void tearDown() throws Exception {
        if (rootNode != null) {
            rootNode.remove();
            session.save();
        }

        super.tearDown();
    }

    public void testJCRType() throws Exception {
        testValue(rootNode, "A String");
        testValue(rootNode, Calendar.getInstance());
        testValue(rootNode, 5L);
        testValue(rootNode, 1.4D);
        testValue(rootNode, true);
        testValue(rootNode, BigDecimal.TEN);
    }

    public void testTypeByClass() throws Exception {
        testValue(rootNode, "A String Value", String.class);

        testValue(rootNode, 1l, Byte.class);
        testValue(rootNode, 1l, Short.class);
        testValue(rootNode, 1l, Integer.class);
        testValue(rootNode, 1l, Long.class);

        testValue(rootNode, 1.0d, Float.class);
        testValue(rootNode, 1.0d, Double.class);
        testValue(rootNode, 1.0d, BigDecimal.class);

        Calendar cal = Calendar.getInstance();
        testValue(rootNode, cal, Calendar.class);
        testValue(rootNode, cal, Date.class);
        testValue(rootNode, cal, Long.class);

        testValue(rootNode, BigDecimal.TEN, BigDecimal.class);
    }

    public void testTypeByDefaultValue() throws Exception {
        testValue(rootNode, "A String Value", "default");

        testValue(rootNode, 1l, (byte) 10);
        testValue(rootNode, 1l, (short) 10);
        testValue(rootNode, 1l, 10);
        testValue(rootNode, 1l, 10l);

        testValue(rootNode, 1.0d, 10.0f);
        testValue(rootNode, 1.0d, 10.0d);
        testValue(rootNode, 1.0d, new BigDecimal("1.0"));

        testValue(rootNode, BigDecimal.TEN, 10.0f);
        testValue(rootNode, BigDecimal.TEN, 10.0d);
        testValue(rootNode, BigDecimal.TEN, BigDecimal.TEN);

        long refTime = 1000l;
        Date refDate = new Date(refTime);
        Calendar refCal = Calendar.getInstance();
        refCal.setTimeInMillis(refTime);

        Calendar cal = Calendar.getInstance();
        testValue(rootNode, cal, refCal);
        testValue(rootNode, cal, refDate);
        testValue(rootNode, cal, refTime);

        testValue(rootNode, BigDecimal.TEN, BigDecimal.ONE);
    }

    public void testDefaultValue() throws Exception {
        testDefaultValue(rootNode, "default");

        testDefaultValue(rootNode, (byte) 10);
        testDefaultValue(rootNode, (short) 10);
        testDefaultValue(rootNode, 10);
        testDefaultValue(rootNode, 10l);

        testDefaultValue(rootNode, 10.0f);
        testDefaultValue(rootNode, 10.0d);
        testDefaultValue(rootNode, new BigDecimal("50.50"));

        long refTime = 1000l;
        Date refDate = new Date(refTime);
        Calendar refCal = Calendar.getInstance();
        refCal.setTimeInMillis(refTime);

        testDefaultValue(rootNode, refCal);
        testDefaultValue(rootNode, refDate);
        testDefaultValue(rootNode, refTime);

        testDefaultValue(rootNode, BigDecimal.TEN);
    }

    public void testProperty() throws Exception {
        ValueMap map = createProperty(rootNode, "Sample Value For Prop");
        Property prop = rootNode.getProperty(PROP_NAME);

        // explicit type
        Property result = map.get(PROP_NAME, Property.class);
        assertTrue(prop.isSame(result));

        // type by default value
        Property defaultValue = rootNode.getProperty("jcr:primaryType");
        result = map.get(PROP_NAME, defaultValue);
        assertTrue(prop.isSame(result));

        // default value
        result = map.get(PROP_NAME_NIL, defaultValue);
        assertSame(defaultValue, result);
    }
    
    public void testInputStream() throws Exception {
        InputStream instream = new ByteArrayInputStream("this too shall pass".getBytes());
        
        ValueFactory valueFactory = rootNode.getSession().getValueFactory();

        rootNode.setProperty("bin", valueFactory.createBinary(instream));
        rootNode.getSession().save();
        
        ValueMap map = new JcrPropertyMap(rootNode);
        instream = map.get("bin", InputStream.class);
        assertNotNull(instream);
        String read = IOUtils.toString(instream);
        assertEquals("Stream read successfully", "this too shall pass", read);
        
        instream = map.get("bin", InputStream.class);
        assertNotNull(instream);
        read = IOUtils.toString(instream);
        assertEquals("Stream read successfully a second time", "this too shall pass", read);
    }

    // ---------- internal

    private void testValue(Node node, Object value, Object defaultValue) throws RepositoryException {
        ValueMap map = createProperty(rootNode, value);
        assertValueType(value, map.get(PROP_NAME, defaultValue), defaultValue.getClass());
    }

    private void testDefaultValue(Node node, Object defaultValue) {
        JcrPropertyMap map = createPropertyMap(rootNode);
        assertSame(defaultValue, map.get(PROP_NAME_NIL, defaultValue));
    }

    private void testValue(Node node, Object value, Class<?> type) throws RepositoryException {
        ValueMap map = createProperty(rootNode, value);
        assertValueType(value, map.get(PROP_NAME, type), type);
    }

    private void assertValueType(Object value, Object result, Class<?> type) {
        assertTrue(type.isInstance(result));

        if (value instanceof Long && result instanceof Number) {
            assertEquals(((Number) value).longValue(), ((Number) result).longValue());

        } else if (value instanceof Double && result instanceof Number) {
            assertEquals(((Number) value).doubleValue(), ((Number) result).doubleValue());

        } else if (value instanceof BigDecimal && result instanceof Number) {
            assertEquals(((BigDecimal) value).doubleValue(), ((Number) result).doubleValue());

        } else if (value instanceof Calendar) {
            long resultTime;
            if (result instanceof Date) {
                resultTime = ((Date) result).getTime();
            } else if (result instanceof Calendar) {
                resultTime = ((Calendar) result).getTimeInMillis();
            } else if (result instanceof Number) {
                resultTime = ((Number) result).longValue();
            } else {
                fail("unexpected result type for Calendar: " + type);
                return;
            }
            assertEquals(((Calendar) value).getTimeInMillis(), resultTime);

        } else {
            assertEquals(value, result);
        }
    }

    protected JcrPropertyMap createPropertyMap(final Node node) {
        return new JcrPropertyMap(node);
    }

    private void testValue(Node node, Object value) throws RepositoryException {
        ValueMap map = createProperty(node, value);
        assertEquals(value, map.get(PROP_NAME));
    }

    private ValueMap createProperty(Node node, Object value)
            throws RepositoryException {
        if (node.hasProperty(PROP_NAME)) {
            node.getProperty(PROP_NAME).remove();
        }

        Value jcrValue;
        ValueFactory fac = session.getValueFactory();
        if (value instanceof String) {
            jcrValue = fac.createValue((String) value);
        } else if (value instanceof Calendar) {
            jcrValue = fac.createValue((Calendar) value);
        } else if (value instanceof Date) {
            Calendar cal = Calendar.getInstance();
            cal.setTime((Date) value);
            jcrValue = fac.createValue(cal);
        } else if (value instanceof Boolean) {
            jcrValue = fac.createValue(((Boolean) value).booleanValue());
        } else if (value instanceof Double) {
            jcrValue = fac.createValue(((Double) value).doubleValue());
        } else if (value instanceof Long) {
            jcrValue = fac.createValue(((Long) value).longValue());
        } else if (value instanceof BigDecimal) {
            jcrValue = fac.createValue((BigDecimal) value);
        } else {
            fail("Cannot create JCR value from " + value);
            return null;
        }

        node.setProperty(PROP_NAME, jcrValue);
        node.getSession().save();

        return createPropertyMap(node);
    }

    private static final String TEST_PATH = "a<a";

    public void testNames() throws Exception {
        this.rootNode.setProperty(ISO9075.encodePath(TEST_PATH), "value");
        final ValueMap vm = this.createPropertyMap(this.rootNode);
        assertEquals("value", vm.get(TEST_PATH));
    }

    public void testIerators() throws Exception {
        this.rootNode.setProperty(ISO9075.encodePath(TEST_PATH), "value");
        final ValueMap vm = this.createPropertyMap(this.rootNode);
        assertTrue(vm.containsKey(TEST_PATH));
        search(vm.keySet().iterator(), TEST_PATH);
        search(vm.values().iterator(), "value");
    }

    public void testDotSlash() throws Exception {
        final String prop = "myProp";
        final String value = "value";
        this.rootNode.setProperty(prop, value);
        final ValueMap vm = this.createPropertyMap(this.rootNode);
        assertEquals(value, vm.get(prop));
        assertEquals(value, vm.get("./" + prop));
        assertTrue(vm.containsKey("./" + prop));
    }

    protected void search(Iterator<?> i, Object value) {
        boolean found = false;
        while ( !found && i.hasNext() ) {
            final Object current = i.next();
            found = current.equals(value);
        }
        if ( !found ) {
            fail("Value " + value + " is not found in iterator.");
        }
    }
}
