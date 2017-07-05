package net.coderodde.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import net.coderodde.util.BidirectionalHashMap.Mapping;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author rodionefremov
 */
public class BidirectionalHashMapTest {
    
    private BidirectionalHashMap<Integer, String> map;
    private BidirectionalHashMap<String, Integer> map2;
    private Map<String, Integer> inverse;
    
    private final String[] strs = { "Hello", "World", 
        "How", "Is", "It", "Going", "?"};
    
    @Before
    public void setUp() {
        map = new BidirectionalHashMap<>(0.5f);
        map2 = new BidirectionalHashMap<>(0.5f);
        inverse = map.inverse();
    }

    @Test
    public void testSize() {
        for (int i = 0; i < strs.length; ++i) {
            assertEquals(i, map.size());
            map.put(i, strs[i]);
            assertEquals(i + 1, map.size());
        }
        
        for (int i = strs.length - 1; i >= 0; --i) {
            assertEquals(i + 1, map.size());
            map.remove(i);
            assertEquals(i, map.size());
        }
    }
    
    @Test
    public void testIsEmpty() {
        assertTrue(map.isEmpty());
        
        for (int i = 0; i < 3; ++i) {
            map.put(i, strs[i]);
            assertFalse(map.isEmpty());
        }
    }

    @Test
    public void testInverse() {
        assertNotNull(map.inverse());
    }

    @Test
    public void testContainsKey() {
        assertFalse(map.containsKey(1));
        map.put(1, "yeah");
        assertTrue(map.containsKey(1));
        map.put(2, "yep");
        map.remove(1);
        assertFalse(map.containsKey(1));
        assertTrue(map.containsKey(2));
        map.remove(2);
        assertFalse(map.containsKey(2));
    }

    @Test
    public void testContainsValue() {
        map.put(10, "Come");
        map.put(20, "on");
        assertTrue(map.containsValue("Come"));
        assertTrue(map.containsValue("on"));
        map.remove(10);
        assertFalse(map.containsValue("Come"));
        assertTrue(map.containsValue("on"));
        map.remove(20);
        assertFalse(map.containsValue("on"));
        
        map.put(50, "50");
        assertTrue(map.containsValue("50"));
        map.put(50, "51");
        assertFalse(map.containsValue("50"));
    }

    @Test
    public void testGet() {
        map.put(100, "100");
        map.put(200, "200");
        
        assertEquals(map.get(100), "100");
        assertEquals(map.get(200), "200");
        
        map.put(200, "blah");
        
        assertEquals(map.get(200), "blah");
    }

    @Test
    public void testPut() {
        map.put(1000, "1000");
        assertTrue(map.containsKey(1000));
        assertTrue(map.containsValue("1000"));
        
        assertFalse(map.containsKey(1001));
        assertFalse(map.containsValue("1001"));
        
        map.remove(1000);
        
        assertFalse(map.containsKey(1000));
        assertFalse(map.containsValue("1000"));
        
        assertNull(map.put(2000, "2000"));
        assertEquals("2000", map.put(2000, "2001"));
        assertEquals("2001", map.put(2000, "2002"));
        
        map.put(20, "A");
        assertEquals("A", map.get(20));
        assertEquals("A", map.put(20, "B"));
        assertEquals("B", map.remove(20));
    }
    
    @Test
    public void testRemove() {
        assertNull(map.remove(1000));
        
        for (int i = 0; i < strs.length; ++i) {
            assertNull(map.put(i, strs[i]));
        }
        
        for (int i = 0; i < strs.length; ++i) {
            assertEquals(strs[i], map.remove(i));
        }
    }

    @Test
    public void testPutAll() {
        for (int i = 0; i < strs.length; ++i) {
            map.put(i + 10, strs[i]);
        }
        
        Map<Integer, String> addMap = new TreeMap<>();
        addMap.put(11, strs[1]);
        addMap.put(14, strs[4]);
        
        assertEquals(strs.length, map.size());
        
        addMap.put(100, "100");
        
        map.putAll(addMap);
        assertEquals(strs.length + 1, map.size());
    }

    @Test
    public void testClear() {
        assertTrue(map.isEmpty());
        
        for (int i = 0; i < strs.length; ++i) {
            map.put(i, strs[i]);
            assertFalse(map.isEmpty());
        }
        
        map.clear();
        assertTrue(map.isEmpty());
    }

    @Test
    public void testKeySet() {
        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");
        map.put(4, "four");
        map.put(5, "five");
        
        Set<Integer> keySet = map.keySet();
        
        int num = 1;
        
        for (Integer i : keySet) {
            assertEquals(Integer.valueOf(num++), i);
        }
        
        Iterator<Integer> iterator = keySet.iterator();
        
        try {
            iterator.remove();
            fail("iterator should have thrown IllegalStateException!");
        } catch (IllegalStateException ex) {
            
        }
        
        assertTrue(iterator.hasNext());
        // Remove 1, 3 and 5:
        iterator.next();
        iterator.remove();
        iterator.next();
        iterator.next();
        iterator.remove();
        iterator.next();
        iterator.next();
        iterator.remove();
        
        iterator = keySet.iterator();
        
        // Check that 2 and 4 are still in the key set:
        assertEquals(Integer.valueOf(2), iterator.next());
        assertEquals(Integer.valueOf(4), iterator.next());
        assertFalse(iterator.hasNext());
        
        try {
            iterator.next();
            fail("BidirectionalHashMap should throw " +
                 "NoSuchElementException here!");
        } catch (NoSuchElementException ex) {
            
        }
    }
    
    @Test(expected = IllegalStateException.class)
    public void 
        testKeySetIteratorThrowsOnConcurrentModificationWhenFirstRemoving() {
        map.put(1, "1");
        map.put(4, "4");
        map.put(7, "7");
        
        Iterator<Integer> keySetIterator = map.keySet().iterator();
        keySetIterator.remove();
    }
        
    @Test(expected = ConcurrentModificationException.class)
    public void 
        testKeySetIteratorThrowsOnConcurrentModificationWhenIterating() {
        map.put(1, "1");
        map.put(4, "4");
        map.put(7, "7");
        
        Iterator<Integer> keySetIterator = map.keySet().iterator();
        map.put(1000, "1000");
        keySetIterator.next();
    }
        
    @Test
    public void testValues() {
        
    }

    @Test
    public void testEntrySet() {
        for (int i = 0; i < strs.length; ++i) {
            map2.put(strs[i], i);
        }
        
        int index = 0;
        
        for (Map.Entry<String, Integer> e : map2.entrySet()) {
            assertEquals(strs[index], e.getKey());
            assertEquals(Integer.valueOf(index), e.getValue());
            index++;
        }
    }

    @Test
    public void testCompact() {
        BidirectionalHashMap<Integer, String> myMap =
                new BidirectionalHashMap<>(0.5f);
        
        for (int i = 0; i < 100; ++i) {
            myMap.put(i, "" + i);
        }
        
        for (int i = 10; i < 100; ++i) {
            myMap.remove(i);
        }
        
        myMap.compact();
        
        assertEquals(10, myMap.size());
        
        for (int i = 0; i < 10; ++i) {
            assertEquals("" + i, myMap.get(i));
        }
    }
    
    @Test
    public void testKeySetSize() {
        for (int i = 0; i < strs.length; ++i) {
            assertEquals(i, map.size());
            map.put(i, strs[i]);
            assertEquals(i + 1, map.size());
        }
    }
    
    @Test
    public void testKeySetIsEmpty() {
        assertTrue(map.isEmpty());
        
        for (int i = 0; i < 3; ++i) {
            map.put(i, strs[i]);
            assertFalse(map.isEmpty());
        }
        
        for (int i = 0; i < 3; ++i) {
            assertFalse(map.isEmpty());
            map.remove(i);
        }
        
        assertTrue(map.isEmpty());
    }
    
    @Test
    public void testKeySetContains() {
        for (int i = 2; i < 5; ++i) {
            assertFalse(map.containsKey(i));
        }
        
        for (int i = 2; i < 5; ++i) {
            assertNull(map.put(i, strs[i]));
        }
        
        for (int i = 2; i < 5; ++i) {
            assertTrue(map.containsKey(i));
        }
    }
    
    @Test
    public void testKeySetToArray() {
        Object[] arr = map.keySet().toArray();
        
        assertEquals(0, arr.length);
        
        for (int test = 0; test < 4; ++test) {
            map.clear();
            
            for (int i = 0; i <= test; ++i) {
                map.put(i, strs[i]);
            }
            
            assertEquals(test + 1, map.size());
            arr = map.keySet().toArray();
            assertEquals(test + 1, arr.length);
            
            Iterator<Integer> iterator = map.keySet().iterator();
            
            for (int index = 0; index < map.keySet().size(); ++index) {
                assertEquals(iterator.next(), arr[index]);
            }
        }
    }
    
    @Test
    public void testKeySetGenericToArray() {
        Integer[] arr = new Integer[3];
        Integer[] arrRet = null;
        
        for (int i = 0; i < 3; ++i) {
            map.put(i, strs[i]);
        }
        
        arrRet = map.keySet().toArray(arr);
        
        assertTrue(arrRet == arr);
        
        Iterator<Integer> iterator = map.keySet().iterator();
        
        for (int i = 0; i < 3; ++i) {
            assertEquals(iterator.next(), arr[i]);
        }
        
        map.put(3, strs[3]);
        arrRet = map.keySet().toArray(arr);
        
        assertNotNull(arrRet);
        assertTrue(arrRet != arr);
        
        iterator = map.keySet().iterator();
        
        for (int i = 0; i < 3; ++i) {
            assertEquals(iterator.next(), arr[i]);
        }
        
        assertTrue(arr != arrRet);
        iterator = map.keySet().iterator();
        
        for (int i = 0; i < map.keySet().size(); ++i) {
            assertEquals(iterator.next(), arrRet[i]);
        }
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void testKeySetAddThrows() {
        map.keySet().add(1);
    }
    
    @Test
    public void testKeySetRemove() {
        Set<Integer> keySet = map.keySet();
        assertFalse(keySet.remove(1));
        map.put(1, "yea");
        assertTrue("yea", keySet.remove(1));
        assertFalse(keySet.remove(10));
        assertFalse("yea", keySet.remove(1));
    }
    
    @Test
    public void testKeySetContainsAll() {
        for (int i = 0; i < strs.length; ++i) {
            map.put(i, strs[i]);
        }
        
        List<Integer> aux = Arrays.asList(1, 4, 5);
        
        assertTrue(map.keySet().containsAll(aux));
        
        aux = Arrays.asList();
        
        assertTrue(map.keySet().containsAll(aux));
        
        aux = Arrays.asList(1, 2, -1);
        
        assertFalse(map.keySet().containsAll(aux));
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void testKeySetAddAll() {
        map.keySet().addAll(Arrays.asList());
    }
    
    @Test
    public void testKeySetRetainAll() {
        for (int i = 1; i < 6; ++i) {
            map.put(i, strs[i]);
        }
        
        Collection<Integer> retainCol = new ArrayList<>();
        
        for (int i = 0; i < 7; ++i) {
            retainCol.add(i);
        }
        
        assertFalse(map.keySet().retainAll(retainCol));
        
        retainCol.remove(2);
        
        assertTrue(map.keySet().retainAll(retainCol));
        
        retainCol.clear();
        
        assertTrue(map.keySet().retainAll(retainCol));
        
        assertFalse(map.keySet().retainAll(retainCol));
    }
    
    @Test
    public void testKeySetRemoveAll() {
        List<Integer> aux = new ArrayList<>();
        
        assertFalse(map.keySet().removeAll(aux));
        
        for (int i = 0; i < strs.length; ++i) {
            map.put(i, strs[i]);
        }
        
        assertFalse(map.keySet().removeAll(aux));
        
        aux.add(-1);
        assertFalse(map.keySet().removeAll(aux));
        
        aux.add(1);
        aux.add(2);
        
        assertTrue(map.keySet().removeAll(aux));
        
        assertEquals(strs.length - 2, map.keySet().size());
    }
    
    @Test
    public void testKeySetClear() {
        assertEquals(0, map.keySet().size());
        
        for (int i = 0; i < strs.length; ++i) {
            map.put(i, strs[i]);
            assertEquals(i + 1, map.keySet().size());
        }
        
        map.keySet().clear();
        assertTrue(map.isEmpty());
        assertTrue(map.keySet().isEmpty());
        assertEquals(0, map.size());
        assertEquals(0, map.keySet().size());
    } 
    
    @Test
    public void testEntrySetSize() {
        Set<Map.Entry<Integer, String>> entrySet = map.entrySet();
        
        for (int i = 0; i < strs.length; ++i) {
            assertEquals(i, entrySet.size());
            entrySet.add(new Mapping<>(i, strs[i]));
            assertEquals(i + 1, entrySet.size());
        }
        
        entrySet.remove(new Mapping<>(2, "yeah"));
        assertEquals(strs.length - 1, entrySet.size());
    }
    
    @Test
    public void testEntrySetIsEmpty() {
        Set<Map.Entry<Integer, String>> entrySet = map.entrySet();
        
        assertTrue(entrySet.isEmpty());
        
        for (int i = 0; i < strs.length; ++i) {
            entrySet.add(new Mapping<>(i, strs[i]));
            assertFalse(entrySet.isEmpty());
        }
        
        entrySet.clear();
        
        assertTrue(entrySet.isEmpty());
    }
    
    @Test
    public void testEntrySetContains() {
        for (int i = 0; i < strs.length; ++i) {
            map.put(i, strs[i]);
        }
        
        Set<Map.Entry<Integer, String>> entrySet = map.entrySet();
        Mapping<Integer, String> keyPair = new Mapping<>(1, "nope");
        assertFalse(entrySet.contains(keyPair));
        
        keyPair = new Mapping<>(2, "World");
        assertFalse(entrySet.contains(keyPair));
        
        for (int i = 0; i < strs.length; ++i) {
            keyPair = new Mapping<>(i, strs[i]);
            assertTrue(entrySet.contains(keyPair));
        }
    }
    
    
    @Test(expected = IllegalStateException.class)
    public void 
        testEntrySetIteratorThrowsOnConcurrentModificationWhenFirstRemoving() {
        map.put(1, "1");
        map.put(4, "4");
        map.put(7, "7");
        
        Iterator<Map.Entry<Integer, String>> entrySetIterator = 
                map.entrySet().iterator();
        
        entrySetIterator.remove();
    }
        
    @Test(expected = ConcurrentModificationException.class)
    public void 
        testEntrySetIteratorThrowsOnConcurrentModificationWhenIterating() {
        map.put(1, "1");
        map.put(4, "4");
        map.put(7, "7");
        
        Iterator<Map.Entry<Integer, String>> entrySetIterator = 
                map.entrySet().iterator();
        map.put(1000, "1000");
        entrySetIterator.next();
    }
        
    @Test
    public void testEntrySetIterator() {
        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");
        map.put(4, "four");
        map.put(5, "five");
        
        Set<Map.Entry<Integer, String>> entrySet = map.entrySet();
        Iterator<Map.Entry<Integer, String>> iterator = entrySet.iterator();
        
        assertTrue(iterator.hasNext());
        assertEquals(new Mapping<>(1, "one"), iterator.next());
        assertEquals(5, entrySet.size());
        
        iterator.remove();
        
        assertEquals(4, entrySet.size());
        assertTrue(iterator.hasNext());
        assertEquals(new Mapping<>(2, "two"), iterator.next());
        
        assertEquals(4, entrySet.size());
        
        assertTrue(iterator.hasNext());
        assertEquals(new Mapping<>(3, "three"), iterator.next());
        
        assertTrue(iterator.hasNext());
        assertEquals(new Mapping<>(4, "four"), iterator.next());
        
        assertTrue(iterator.hasNext());
        assertEquals(new Mapping<>(5, "five"), iterator.next());
        
        assertFalse(iterator.hasNext());
        
        iterator.remove();
        
        try {
            iterator.remove();
            fail("Iterator should have thrown IllegalStateException!");
        } catch (IllegalStateException ex) {
            
        }
        
        try {
            iterator.next();
            fail("Iterator should have thrown NoSuchElementException!");
        } catch (NoSuchElementException ex) {
            
        }        
    }
    
    @Test
    public void testEntrySetToArray() {
        loadMap();
        Object[] arr = map.entrySet().toArray();
        
        assertEquals(strs.length, arr.length);
        Iterator<Map.Entry<Integer, String>> iterator =
                map.entrySet().iterator();
        
        for (int i = 0; i < strs.length; ++i) {
            assertEquals(iterator.next(), arr[i]);
        }
    }
    
    @Test
    public void testEntrySetGenericToArray() {
        loadMap();
        Map.Entry<Integer, String>[] arr = new Map.Entry[strs.length - 1];
        Map.Entry<Integer, String>[] retArr;
        
        retArr = map.entrySet().toArray(arr);
        assertFalse(retArr == arr);
        Iterator<Map.Entry<Integer, String>> iterator = 
                map.entrySet().iterator();
        
        for (int i = 0; i < retArr.length; ++i) {
            Map.Entry<Integer, String> entry =
                    (Map.Entry<Integer, String>) retArr[i];
            assertEquals(iterator.next(), entry);
        }
        
        arr = new Map.Entry[strs.length];
        retArr = map.entrySet().toArray(arr);
        assertTrue(retArr == arr);
        iterator = map.entrySet().iterator();
        
        for (int i = 0; i < retArr.length; ++i) {
            Map.Entry<Integer, String> entry =
                    (Map.Entry<Integer, String>) retArr[i];
            assertEquals(iterator.next(), entry);
        }
    }
    
    @Test(expected = NullPointerException.class)
    public void testEntrySetThrowsOnNullGenericArray() {
        map.entrySet().toArray(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void testKeySetThrowsOnNullGenericArray() {
        map.keySet().toArray(null);
    }
    
    @Test
    public void testEntrySetAdd() {
        Set<Map.Entry<Integer, String>> entrySet = map.entrySet();
        
        for (int i = 0; i < strs.length; ++i) {
            assertTrue(entrySet.add(new Mapping<>(i, strs[i])));
        }
        
        for (int i = 0; i < strs.length; ++i) {
            // No duplicates allowed.
            assertFalse(entrySet.add(new Mapping<>(i, strs[i])));
        }
        
        assertTrue(entrySet.add(new Mapping<>(1, "new one")));
        assertFalse(entrySet.add(new Mapping<>(1, "new one")));
        assertTrue(entrySet.add(new Mapping<>(-1, "yo!")));
        assertFalse(entrySet.add(new Mapping<>(-1, "yo!")));
        assertEquals("new one", map.get(Integer.valueOf(1)));
    }
    
    @Test
    public void testEntrySetRemove() {
        loadMap();
        Set<Map.Entry<Integer, String>> entrySet = map.entrySet();
        
        for (int i = 0; i < strs.length; ++i) {
            assertTrue(entrySet.remove(new Mapping<>(i, strs[i])));
        }
        
        assertFalse(entrySet.remove(new Mapping<>(0, "none")));
        assertFalse(entrySet.remove(new Mapping<>(-1, "Hello")));
        
        for (int i = 0; i < strs.length; ++i) {
            // Cannot remove twice.
            assertFalse(entrySet.remove(new Mapping<>(i, strs[i])));
        }
    }
    
    @Test
    public void testEntrySetContainsAll() {
        for (int i = 0; i <= 5; ++i) {
            map.put(i, "" + i);
        }
        
        Set<Map.Entry<Integer, String>> entrySet = map.entrySet();
        Set<Map.Entry<Integer, String>> set = new HashSet<>();
        
        set.add(new Mapping<>(1, "1"));
        set.add(new Mapping<>(2, "2"));
        set.add(new Mapping<>(3, "3"));
        set.add(new Mapping<>(4, "4"));
        
        assertTrue(entrySet.containsAll(set));
        
        set.add(new Mapping<>(-1, "1"));
        
        assertFalse(entrySet.containsAll(set));
        
        set.clear();
        assertTrue(entrySet.containsAll(set));
    }
    
    @Test
    public void testEntrySetAddAll() {
        Set<Map.Entry<Integer, String>> set = new HashSet<>();
        Set<Map.Entry<Integer, String>> entrySet = map.entrySet();
        
        assertFalse(entrySet.addAll(set));
        set.add(new Mapping<>(1, "1"));
        
        assertTrue(entrySet.addAll(set));
        assertEquals(1, entrySet.size());
        assertFalse(entrySet.addAll(set));
        
        set.add(new Mapping<>(2, "2"));
        set.add(new Mapping<>(3, "3"));
        
        assertTrue(entrySet.addAll(set));
        assertFalse(entrySet.addAll(set));
        
        assertTrue(entrySet.contains(new Mapping<>(1, "1")));
        assertTrue(entrySet.contains(new Mapping<>(2, "2")));
        assertTrue(entrySet.contains(new Mapping<>(3, "3")));
        assertFalse(entrySet.contains(new Mapping<>(2, "-2")));
    }
    
    @Test
    public void testEntrySetRetainAll() {
        loadMap();
        Set<Map.Entry<Integer, String>> set = new HashSet<>();
        Set<Map.Entry<Integer, String>> entrySet = map.entrySet();
        
        for (int i = 0; i < strs.length; ++i) {
            set.add(new Mapping<>(i, strs[i]));
        }
        
        assertFalse(entrySet.retainAll(set));
        assertTrue(entrySet.contains(new Mapping<>(1, "World")));
        set.remove(new Mapping<>(1, "World"));
        assertTrue(entrySet.retainAll(set));
        assertFalse(entrySet.contains(new Mapping<>(1, "World")));
        
        assertEquals(strs.length - 1, entrySet.size());
        
        assertFalse(entrySet.contains(new Mapping<>(1, "World")));
        assertTrue(entrySet.contains(new Mapping<>(4, "It")));
        set.remove(new Mapping<>(4, "It"));
        assertTrue(entrySet.retainAll(set));
        assertFalse(entrySet.retainAll(set));
        assertFalse(entrySet.contains(new Mapping<>(1, "World")));
        assertFalse(entrySet.contains(new Mapping<>(4, "It")));
    }
    
    @Test
    public void testEntrySetRemoveAll() {
        loadMap();
        Set<Map.Entry<Integer, String>> set = new HashSet<>();
        Set<Map.Entry<Integer, String>> entrySet = map.entrySet();
        
        assertFalse(entrySet.removeAll(set));
        
        set.add(new Mapping<>(1, "World"));
        
        assertTrue(entrySet.removeAll(set));
        assertFalse(entrySet.removeAll(set));
        
        set.add(new Mapping<>(100, "oh yeah"));
        
        assertFalse(entrySet.removeAll(set));
        assertEquals(strs.length - 1, entrySet.size());
        
        for (int i = 0; i < strs.length; ++i) {
            set.add(new Mapping<>(i, strs[i]));
        }   
        
        assertTrue(entrySet.removeAll(set));
        assertFalse(entrySet.removeAll(set));
        assertTrue(entrySet.isEmpty());
    }
    
    @Test
    public void testEntrySetClear() {
        Set<Map.Entry<Integer, String>> entrySet = map.entrySet();
        assertEquals(0, entrySet.size());
        
        for (int i = 0; i < strs.length; ++i) {
            assertEquals(i, entrySet.size());
            entrySet.add(new Mapping<>(i, strs[i]));
            assertEquals(i + 1, entrySet.size());
        }
        
        entrySet.clear();
        assertEquals(0, entrySet.size());
    }
    
    @Test
    public void testInverseMapSize() {
        
        for (int i = 0; i < strs.length; ++i) {
            assertEquals(i, inverse.size());
            inverse.put(strs[i], i);
            assertEquals(i + 1, inverse.size());
        }
    }
    
    @Test
    public void testInverseMapIsEmpty() {
        assertTrue(inverse.isEmpty());
        
        for (int i = 0; i < strs.length; ++i) {
            assertNull(inverse.put(strs[i], i));
            assertFalse(inverse.isEmpty());
        }
    }
    
    @Test
    public void testInverseMapContainsKey() {
        loadMap();
        assertTrue(inverse.containsKey("World"));
        assertTrue(inverse.containsKey("Hello"));
        assertFalse(inverse.containsKey("no"));
        assertFalse(inverse.containsKey("neither"));
    }
    
    @Test
    public void testInverseMapContainsValue() {
        loadMap();
        assertTrue(inverse.containsValue(0));
        assertTrue(inverse.containsValue(1));
        assertFalse(inverse.containsValue(-1));
    }
    
    @Test
    public void testInverseMapGet() {
        loadMap();
        
        for (int i = 0; i < strs.length; ++i) {
            assertEquals(Integer.valueOf(i), inverse.get(strs[i]));
        }
        
        assertNull(inverse.get("Funky"));
        assertNull(inverse.get("not quite"));
    }
    
    @Test
    public void testInverseMapPut() {
        assertNull(inverse.put("Hey", 1));
        assertNull(inverse.put("yo!", 2));
        
        assertEquals(Integer.valueOf(1), inverse.put("Hey", 11));
        assertEquals(Integer.valueOf(2), inverse.put("yo!", 12));
        
        assertEquals(Integer.valueOf(12), inverse.remove("yo!"));
        assertEquals(Integer.valueOf(11), inverse.remove("Hey"));
    }
    
    
    
    private void loadMap() {
        for (int i = 0; i < strs.length; ++i) {
            map.put(i, strs[i]);
        }
    }
}
