package net.coderodde.util;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * This class implements a bidirectional, bijective hash map. It asks two key 
 * types: a primary key type and a secondary key type. Both must be 
 * {@code Comparable}. It allows creating mappings, which are just primary/
 * secondary -key pairs, and later accessing secondary keys via primary keys,
 * and vice versa.
 * <p>
 * Suppose we have a mapping {@code (1, A)}. If we access it via primary key,
 * {@code A} will be returned. If we access it via secondary key, {@code 1} will
 * be returned. When asking to put, say, {@code (1, B)} via primary key, the 
 * aforementioned mapping will become {@code (1, B)}. Conversely, you can update
 * {@code (1, A)} via secondary key: you can put {@code (2, A)} via secondary 
 * key which will update the given mapping to {@code (2, A)}.
 * <p>
 * As a slight optimization, of each mapping, both primary and secondary key 
 * hashes are cached which might give a slight performance advantage when
 * dealing, say, with strings or other containers.
 * <p>
 * Also, unlike most hash tables that maintain collision chains in order to
 * store hash ties, this implementation implements the collision chains as 
 * AVL-trees, which guarantees that no (not resizing) operation runs in time
 * worse than {@code O(log n)}.
 * <p>
 * If a user needs to optimize the memory usage of this hash map, a method 
 * {@code compact()} is provided which effectively will try to compact the 
 * underlying hash tables to a smallest size (power of two) that does not exceed
 * the given maximum load factor.
 * <p>
 * Since this hash map may become sparse (by first adding many mappings and then
 * removing most of them) the primary mappings maintain a doubly-linked list
 * which provides faster iteration and faster moving of all the mappings to a 
 * new hash tables when expanding or compacting the map.
 * 
 * @author Rodion "rodde" Efremov 
 * @version 1.6 (Jul 2, 2017)
 * @param <K1> the type of the primary keys.
 * @param <K2> the type of the secondary keys.
 */
public final class BidirectionalHashMap<K1 extends Comparable<? super K1>, 
                                        K2 extends Comparable<? super K2>>
        implements Map<K1, K2> {

    @Override
    public void putAll(Map<? extends K1, ? extends K2> m) {
        for (Map.Entry<? extends K1, ? extends K2> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    /**
     * The class for holding primary and secondary keys and their respective
     * hash values.
     * 
     * @param <K1> the type of the primary keys.
     * @param <K2> the type of the secondary keys.
     */
    public static final class KeyPair<K1, K2> implements Map.Entry<K1, K2> {
        
        /**
         * The primary key.
         */
        private K1 primaryKey;
        
        /**
         * The secondary key.
         */
        private K2 secondaryKey;
        
        /**
         * The hash value of the primary key. We cache this in order to have a
         * slight performance advantage when dealing with, say, strings or other
         * containers.
         */
        private int primaryKeyHash;
        
        /**
         * The hash value of the secondary key.
         */
        private int secondaryKeyHash;

        /**
         * Constructs a key pair.
         * 
         * @param primaryKey   the primary key;
         * @param secondaryKey the secondary key.
         */
        KeyPair(K1 primaryKey, K2 secondaryKey) {
            this.primaryKey = 
                    Objects.requireNonNull(
                        primaryKey,
                        "This BidirectionalHashMap does not permit null keys.");
            
            this.secondaryKey = 
                    Objects.requireNonNull(
                        secondaryKey,
                        "This BidirectionalHashMap does not permit null keys.");
            
            this.primaryKeyHash = primaryKey.hashCode();
            this.secondaryKeyHash = secondaryKey.hashCode();
        }
        
        @Override
        public K1 getKey() {
            return primaryKey;
        }

        @Override
        public K2 getValue() {
            return secondaryKey;
        }

        @Override
        public K2 setValue(K2 value) {
            throw new UnsupportedOperationException(
                    "Changing secondary key is not supported!");
        }
        
        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }
            
            if (o == this) {
                return true;
            }
            
            if (!getClass().equals(o.getClass())) {
                return false;
            }
            
            KeyPair<K1, K2> other = (KeyPair<K1, K2>) o;
            
            return primaryKey.equals(other.primaryKey) && 
                    secondaryKey.equals(other.secondaryKey);
        }
        
        @Override
        public int hashCode() {
            return primaryKeyHash ^ secondaryKeyHash;
        }
    }
    
    /**
     * Implements the basics of a collision tree nodes.
     * 
     * @param <K1> the type of the primary keys.
     * @param <K2> the type of the secondary keys.
     */
    private static abstract class AbstractCollisionTreeNode
            <K1 extends Comparable<? super K1>,
             K2 extends Comparable<? super K2>> {
        
        /**
         * The parent node of this collision tree node. Set to {@code null} if
         * this node is a root.
         */
        AbstractCollisionTreeNode<K1, K2> parent;
        
        /**
         * The left child node of this node.
         */
        AbstractCollisionTreeNode<K1, K2> leftChild;
        
        /**
         * The right child node of this node.
         */
        AbstractCollisionTreeNode<K1, K2> rightChild;
        
        /**
         * The height of this tree node.
         */
        int height;
        
        /**
         * The key pair.
         */
        KeyPair<K1, K2> keyPair;
    }
        
    /**
     * Implements the primary collision tree node which maintains an additional
     * doubly linked list for faster iteration and remapping when the load
     * factor is exceeded.
     * 
     * @param <K1> the type of the primary keys.
     * @param <K2> the type of the secondary keys.
     */
    private static final class 
            PrimaryCollisionTreeNode<K1 extends Comparable<? super K1>, 
                                     K2 extends Comparable<? super K2>> 
            extends AbstractCollisionTreeNode<K1, K2> {
        
        /**
         * The last primary collision tree node that was added before this node.
         */
        PrimaryCollisionTreeNode<K1, K2> up;
        
        /**
         * The first primary collision tree node that was added after this node.
         */
        PrimaryCollisionTreeNode<K1, K2> down;
    }
    
    /**
     * Implements a secondary collision tree node.
     * @param <K1> the type of the primary key.
     * @param <K2> the type of the secondary key.
     */
    private static final class 
            SecondaryCollisionTreeNode<K1 extends Comparable<? super K1>, 
                                       K2 extends Comparable<? super K2>> 
            extends AbstractCollisionTreeNode<K1, K2> {
        
    }
    
    /**
     * Minimum capacity of the hash tables.
     */
    private static final int MINIMUM_INITIAL_CAPACITY = 8;
    
    /**
     * The smallest upper bound for the maximum load factor.
     */
    private static final float SMALLEST_MAXIMUM_LOAD_FACTOR = 0.2f;
    
    /**
     * The default hash table capacity.
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 8;
    
    /**
     * The default maximum load factor.
     */
    private static final float DEFAULT_MAXIMUM_LOAD_FACTOR = 1.0f;
    
    /**
     * The number of primary/secondary key mappings in this map.
     */
    private int size;
    
    /**
     * The hash table containing primary collision trees. The length of this 
     * array will be always a power of two.
     */
    private PrimaryCollisionTreeNode<K1, K2>[] primaryHashTable;
    
    /**
     * The hash table containing secondary collision trees. The length of this
     * array will be always equal to the length of {@code primaryHashTable} and
     * thus will be always a power of two.
     */
    private SecondaryCollisionTreeNode<K1, K2>[] secondaryHashTable;
    
    /**
     * The maximum load factor.
     */
    private final float maximumLoadFactor;
    
    /**
     * The binary mask used to compute modulo of hash values. Since the hash 
     * tables are always of length that is a power of two. This mask will always
     * be {@code 2^k - 1} for some integer {@code k}.
     */
    private int moduloMask;
    
    /**
     * The head node of the iteration list.
     */
    private PrimaryCollisionTreeNode<K1, K2> iterationListHead;
    
    /**
     * The tail node of the iteration list.
     */
    private PrimaryCollisionTreeNode<K1, K2> iterationListTail;
    
    /**
     * The inverse map mapping the keys in opposite order.
     */
    private Map<K2, K1> inverseMap;
    
    /**
     * Used for keeping track of modification during iteration.
     */
    private int modificationCount;
    
    private class InverseMap implements Map<K2, K1> {

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

        @Override
        public boolean containsKey(Object key) {
            AbstractCollisionTreeNode<K1, K2> node = 
                    getSecondaryCollisionTreeNode((K2) key);
            
            return node != null;
        }

        @Override
        public boolean containsValue(Object value) {
            AbstractCollisionTreeNode<K1, K2> node = 
                    getPrimaryCollisionTreeNode((K1) value);
            
            return node != null;
        }

        @Override
        public K1 get(Object key) {
            AbstractCollisionTreeNode<K1, K2> node = 
                    getSecondaryCollisionTreeNode((K2) key);
            
            if (node != null) {
                return node.keyPair.primaryKey;
            } else {
                return null;
            }
        }

        @Override
        public K1 put(K2 secondaryKey, K1 primaryKey) {
            AbstractCollisionTreeNode<K1, K2> node =
                    getSecondaryCollisionTreeNode(secondaryKey);
            
            K1 oldPrimaryKey;
            
            if (node != null) {
                oldPrimaryKey = node.keyPair.primaryKey;
                node.keyPair.primaryKey = primaryKey;
                node.keyPair.primaryKeyHash = primaryKey.hashCode();
                
                if (!primaryKey.equals(oldPrimaryKey)) {
                    ++modificationCount;
                }
                
                return oldPrimaryKey;
            } else {
                addNewMapping(primaryKey, secondaryKey);
                ++modificationCount;
                ++size;
                return null;
            }
        }

        @Override
        public K1 remove(Object key) {
            AbstractCollisionTreeNode<K1, K2> node = 
                    getSecondaryCollisionTreeNode((K2) key);
            
            if (node == null) {
                return null;
            }
            
            K1 oldPrimaryKey = node.keyPair.primaryKey;
            int hashCode = node.keyPair.primaryKeyHash;
            int secondaryCollisionTreeBucketIndex = hashCode & moduloMask;
            AbstractCollisionTreeNode<K1, K2> oppositeNode =
                    getPrimaryTreeNodeViaSecondaryTreeNode(null);
            
            int oppositeNodeHashCode = oppositeNode.keyPair.primaryKeyHash;
            int primaryCollisionTreeBucketIndex = oppositeNodeHashCode 
                                                & moduloMask;
            
            unlinkCollisionTreeNode(node,
                                    secondaryHashTable,
                                    secondaryCollisionTreeBucketIndex);
            
            unlinkCollisionTreeNode(oppositeNode,
                                    primaryHashTable,
                                    primaryCollisionTreeBucketIndex);
            
            unlinkPrimaryCollisionTreeNodeFromIterationChain(
                    (PrimaryCollisionTreeNode<K1, K2>) oppositeNode);
            
            ++modificationCount;
            --size;
            return oldPrimaryKey;
        }

        @Override
        public void putAll(Map<? extends K2, ? extends K1> m) {
            for (Map.Entry<? extends K2, ? extends K1> e: m.entrySet()) {
                BidirectionalHashMap.this.put(e.getValue(), e.getKey());
            }
        }

        @Override
        public void clear() {
            BidirectionalHashMap.this.clear();
        }

        @Override
        public Set<K2> keySet() {
            return new InverseKeySet();
        }

        @Override
        public Collection<K1> values() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Set<Entry<K2, K1>> entrySet() {
            throw new UnsupportedOperationException(
                    "Not supported yet. Use BidirectionalHashMap.entrySet() " +
                    "instead.");
        }
        
        private final class InverseKeySet implements Set<K2> {

            @Override
            public int size() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean isEmpty() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean contains(Object o) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public Iterator<K2> iterator() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public Object[] toArray() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public <T> T[] toArray(T[] a) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean add(K2 e) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean remove(Object o) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean containsAll(Collection<?> c) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean addAll(Collection<? extends K2> c) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean retainAll(Collection<?> c) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean removeAll(Collection<?> c) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
            
        }
    }
    
    public BidirectionalHashMap(int initialCapacity,
                                float maximumLoadFactor) {
        initialCapacity = Math.max(initialCapacity, MINIMUM_INITIAL_CAPACITY);
        maximumLoadFactor = Math.max(maximumLoadFactor, 
                                     SMALLEST_MAXIMUM_LOAD_FACTOR);
        
        initialCapacity = roundToPowerOfTwo(initialCapacity);
        
        this.maximumLoadFactor = maximumLoadFactor;
        this.primaryHashTable = new PrimaryCollisionTreeNode[initialCapacity];
        this.secondaryHashTable = 
                new SecondaryCollisionTreeNode[initialCapacity];
        this.moduloMask = initialCapacity - 1;
    }
    
    public BidirectionalHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_MAXIMUM_LOAD_FACTOR);
    }
    
    public BidirectionalHashMap(float maximumLoadFactor) {
        this(DEFAULT_INITIAL_CAPACITY, maximumLoadFactor);
    }
    
    public BidirectionalHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_MAXIMUM_LOAD_FACTOR);
    }
    
    public float getCurrentLoadFactor() {
        return 1.0f * size / primaryHashTable.length;
    }
    
    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    public Map<K2, K1> inverse() {
        if (inverseMap == null) {
            inverseMap = new InverseMap();
        }
        
        return inverseMap;
    }
    
    @Override
    public boolean containsKey(Object key) {
        AbstractCollisionTreeNode<K1, K2> collisionTreeNode = 
                getPrimaryCollisionTreeNode((K1) key);
        
        return collisionTreeNode != null;
    }
    
    private AbstractCollisionTreeNode<K1, K2> 
        getPrimaryCollisionTreeNode(K1 primaryKey) {
        int hashCode = primaryKey.hashCode();
        int primaryCollisionTreeBucketIndex = hashCode & moduloMask;
        
        AbstractCollisionTreeNode<K1, K2> node = 
                primaryHashTable[primaryCollisionTreeBucketIndex];
        
        while (node != null) {
            int cmp = primaryKey.compareTo(node.keyPair.primaryKey);
            
            if (cmp < 0) {
                node = node.leftChild;
            } else if (cmp > 0) {
                node = node.rightChild;
            } else {
                return node;
            }
        }
        
        return null;
    }

    private AbstractCollisionTreeNode<K1, K2>
            getSecondaryCollisionTreeNode(K2 secondaryKey) {
        int hashCode = secondaryKey.hashCode();
        int secondaryCollisionTreeBucketIndex = hashCode & moduloMask;
        
        AbstractCollisionTreeNode<K1, K2> node = 
                secondaryHashTable[secondaryCollisionTreeBucketIndex];
        
        while (node != null) {
            int cmp = secondaryKey.compareTo(node.keyPair.secondaryKey);
            
            if (cmp < 0) {
                node = node.leftChild;
            } else if (cmp > 0) {
                node = node.rightChild;
            } else {
                return node;
            }
        }
        
        return null;
    }
        
    @Override
    public boolean containsValue(Object value) {
        AbstractCollisionTreeNode<K1, K2> node = 
                getSecondaryCollisionTreeNode((K2) value);
        
        return node != null;
    }

    @Override
    public K2 get(Object key) {
        AbstractCollisionTreeNode<K1, K2> node =
                getPrimaryCollisionTreeNode((K1) key);
        
        if (node != null) {
            return node.keyPair.secondaryKey;
        } else {
            return null;
        }
    }

    @Override
    public K2 put(K1 key, K2 value) {
        AbstractCollisionTreeNode<K1, K2> node = 
                getPrimaryCollisionTreeNode(key);
        
        K2 oldValue;
        
        if (node != null) {
            oldValue = node.keyPair.secondaryKey;
            node.keyPair.secondaryKey = value;
            node.keyPair.secondaryKeyHash = value.hashCode();
            
            if (!value.equals(oldValue)) {
                ++modificationCount;
            }
            
            return oldValue;
        } else {
            addNewMapping(key, value);
            ++modificationCount;
            ++size;
            return null;
        }
    }

    @Override
    public K2 remove(Object key) {
        AbstractCollisionTreeNode<K1, K2> node = 
                getPrimaryCollisionTreeNode((K1) key);
        
        if (node == null) {
            return null;
        }
        
        K2 oldValue = node.keyPair.secondaryKey;
        int hashCode = node.keyPair.primaryKeyHash;
        int primaryCollisionTreeBucketIndex = hashCode & moduloMask;
        AbstractCollisionTreeNode<K1, K2> oppositeNode = 
                getSecondaryTreeNodeViaPrimaryTreeNode(
                        (PrimaryCollisionTreeNode<K1, K2>) node);
        
        int oppositeNodeHashCode = oppositeNode.keyPair.secondaryKeyHash;
        int secondaryCollisionTreeBucketIndex = oppositeNodeHashCode 
                                              & moduloMask;
        
        unlinkCollisionTreeNode(node,
                                primaryHashTable, 
                                primaryCollisionTreeBucketIndex);
        
        unlinkCollisionTreeNode(oppositeNode,
                                secondaryHashTable,
                                secondaryCollisionTreeBucketIndex);
        
        unlinkPrimaryCollisionTreeNodeFromIterationChain(
                (PrimaryCollisionTreeNode<K1, K2>) node);
        
        ++modificationCount;
        --size;
        return oldValue;
    }

    @Override
    public void clear() {
        PrimaryCollisionTreeNode<K1, K2> node = iterationListHead;
        
        for (; node != null; node = node.down) {
            int primaryCollisionTreeBucketIndex = node.keyPair.primaryKeyHash
                                                & moduloMask;
            
            int secondaryCollisionTreeBucketIndex = 
                    node.keyPair.secondaryKeyHash & moduloMask;
            
            primaryHashTable[primaryCollisionTreeBucketIndex] = null;
            secondaryHashTable[secondaryCollisionTreeBucketIndex] = null;
        }
        
        modificationCount += size;
        size = 0;
    }

    @Override
    public Set<K1> keySet() {
        return new KeySet();
    }
    
    private final class KeySet implements Set<K1> {

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

        @Override
        public boolean contains(Object o) {
            return BidirectionalHashMap.this.containsKey((K1) o);
        }

        @Override
        public Iterator<K1> iterator() {
            return new KeySetIterator();
        }
        
        private final class KeySetIterator implements Iterator<K1> {

            private int expectedModificationCount = modificationCount;
            private int cachedSize = size;
            
            private PrimaryCollisionTreeNode<K1, K2> currentNode =
                    iterationListHead;
            
            private PrimaryCollisionTreeNode<K1, K2> lastIteratedNode = null;
            
            private int iterated = 0;
            private boolean canRemove = false;
            
            @Override
            public boolean hasNext() {
                return iterated < cachedSize;
            }

            @Override
            public K1 next() {
                checkModificationCount(expectedModificationCount);
                
                if (!hasNext()) {
                    throw new NoSuchElementException(
                            "There is no next key to iterate!");
                }
                
                lastIteratedNode = currentNode;
                K1 ret = currentNode.keyPair.primaryKey;
                currentNode = currentNode.down;
                canRemove = true;
                ++iterated;
                return ret;
                
            }
            
            @Override
            public void remove() {
                if (!canRemove) {
                    if (iterated == 0) {
                        throw new IllegalStateException(
                                "'next()' is not called at least once. " +
                                "Nothing to remove!");
                    } else {
                        throw new IllegalStateException(
                                "Cannot remove a key twice!");
                    }
                }
                
                checkModificationCount(expectedModificationCount);
                BidirectionalHashMap
                        .this.remove(lastIteratedNode.keyPair.primaryKey);
                canRemove = false;
                expectedModificationCount = modificationCount;
            }
        }

        @Override
        public Object[] toArray() {
            Object[] array = new Object[size];
            int index = 0;
            
            for (K1 key : this) {
                array[index++] = key;
            }
            
            return array;
        }

        @Override
        public <T> T[] toArray(T[] a) {
            Objects.requireNonNull(a, "The input array is null.");
            
            if (a.length < size) {
                T[] array = 
                        (T[]) Array.newInstance(a.getClass()
                                                 .getComponentType(), size);
                int index = 0;
                
                for (K1 key : this) {
                    array[index++] = (T) key;
                }
                
                return array;
            }
            
            int index = 0;

            for (K1 key : this) {
                a[index++] = (T) key;
            }

            if (a.length > size) {
                a[size] = null;
            }

            return a;
        }

        @Override
        public boolean add(K1 e) {
            throw new UnsupportedOperationException(
                    "add() is not supported!");
        }

        @Override
        public boolean remove(Object o) {
            boolean contains = BidirectionalHashMap.this.containsKey(o);
            
            if (contains) {
                BidirectionalHashMap.this.remove(o);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            for (Object o : c) {
                if (!contains(o)) {
                    return false;
                }
            }
            
            return true;
        }

        @Override
        public boolean addAll(Collection<? extends K1> c) {
            throw new UnsupportedOperationException(
                    "addAll() is not supported!");
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            boolean modified = false;
            Iterator<K1> iterator = iterator();
            
            while (iterator.hasNext()) {
                K1 key = iterator.next();
                
                if (!c.contains(key)) {
                    modified = true;
                    iterator.remove();
                }
            }
            
            return modified;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean modified = false;
            
            for (Object o : c) {
                if (remove(o)) {
                    modified = true;
                }
            }
            
            return modified;
        }

        @Override
        public void clear() {
            BidirectionalHashMap.this.clear();
        }
    }

    @Override
    public Collection<K2> values() {
        throw new UnsupportedOperationException(
                "values() not implemented. Use inverse() instead.");
    }
    
    @Override
    public Set<Entry<K1, K2>> entrySet() {
        return new EntrySet();
    }
    
    private class EntrySet implements Set<Entry<K1, K2>> {

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

        @Override
        public boolean contains(Object o) {
            Objects.requireNonNull(o, "The input map entry is null!");
            KeyPair<K1, K2> keyPair = (KeyPair<K1, K2>) o;
            AbstractCollisionTreeNode<K1, K2> node = 
                    getPrimaryCollisionTreeNode(keyPair.primaryKey);
            
            if (node == null) {
                return false;
            }
            
            AbstractCollisionTreeNode<K1, K2> oppositeNode =
                    BidirectionalHashMap.this
                            .getSecondaryTreeNodeViaPrimaryTreeNode(
                                    (PrimaryCollisionTreeNode<K1, K2>) node);
            
            return node != null 
                    && oppositeNode.keyPair
                                   .secondaryKey.equals(keyPair.secondaryKey);
        }

        @Override
        public Iterator<Entry<K1, K2>> iterator() {
            return new KeyPairIterator();
        }
        
        private final class KeyPairIterator implements Iterator<Entry<K1, K2>>{

            /**
             * Caches the modification count of the owning BidirectionalHashMap.
             */
            private int expectedModificationCount = modificationCount;
            
            /**
             * How many key pairs in total we need to visit. We cache this as
             * we may remove key pairs via remove() method.
             */
            private int cachedSize = size;
            
            /**
             * A pointer to a current node.
             */
            private PrimaryCollisionTreeNode<K1, K2> currentNode = 
                    iterationListHead;
            
            /**
             * Holds a node last iterated over with next(). It is set to null
             * whenever we remove a node via remove() and haven't yet called
             * next() in order to advance to a node that is removable.
             */
            private PrimaryCollisionTreeNode<K1, K2> lastIteratedNode = null;
            
            /**
             * Caches the number of elements iterated via next().
             */
            private int iterated = 0;
            
            /**
             * Indicates whether we are pointing to a valid current node that is
             * possible to remove.
             */
            private boolean canRemove = false;
            
            @Override
            public boolean hasNext() {
                return iterated < cachedSize;
            }

            @Override
            public Entry<K1, K2> next() {
                checkModificationCount(expectedModificationCount);
                
                if (!hasNext()) {
                    throw new NoSuchElementException(
                            "There is no next key pair to iterate!");
                }
                
                lastIteratedNode = currentNode;
                KeyPair<K1, K2> ret = currentNode.keyPair;
                currentNode = currentNode.down;
                canRemove = true;
                ++iterated;
                return ret;
            }
            
            public void remove() {
                if (!canRemove) {
                    if (iterated == 0) {
                        throw new IllegalStateException(
                                "'next()' is not called at least once. " +
                                "Nothing to remove!");
                    } else {
                        throw new IllegalStateException(
                                "Cannot remove a key pair twice!");
                    }
                }
                
                checkModificationCount(expectedModificationCount);
                BidirectionalHashMap
                        .this.remove(lastIteratedNode.keyPair.primaryKey);
                canRemove = false;
                expectedModificationCount = modificationCount;
            }
        }

        @Override
        public Object[] toArray() {
            Object[] array = new Object[size];
            int index = 0;
            
            for (Map.Entry<K1, K2> e : this) {
                array[index++] = e;
            }
            
            return array;
        }

        @Override
        public <T> T[] toArray(T[] a) {
            Objects.requireNonNull(a, "The input array is null.");
            
            if (a.length < size) {
                T[] array =
                        (T[]) Array.newInstance(a.getClass()
                                                 .getComponentType(), size);
                
                int index = 0;
                
                for (Map.Entry<K1, K2> entry : this) {
                   array[index++] = (T) entry;
                }
                
                return array;
            }
            
            int index = 0;
            
            for (Map.Entry<K1, K2> e : this) {
                a[index++] = (T) e;
            }
            
            if (a.length > size) {
                a[size] = null;
            }
            
            return a;
        }

        @Override
        public boolean add(Map.Entry<K1, K2> e) {
            Object o = put(e.getKey(), e.getValue());
            return !Objects.equals(o, e.getValue());
        }

        /**
         * This method expects a {@link KeyPair} as input. Removes a mapping
         * from the owner bidirectional map via the primary key of the input
         * mapping; that is, the secondary key is ignored.
         * 
         * @param o the key mapping as an {@code Object}.
         * @return {@code true} if the underlying map changed due to the call.
         */
        @Override
        public boolean remove(Object o) {
            return BidirectionalHashMap
                    .this.remove(((KeyPair<K1, K2>) o).primaryKey) != null;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            for (Object o : c) {
                if (!contains(o)) {
                    return false;
                }
            }
            
            return true;
        }

        @Override
        public boolean addAll(Collection<? extends Entry<K1, K2>> c) {
            boolean changed = false;
            
            for (Entry<K1, K2> e : c) {
                if (add(e)) {
                    changed = true;
                }
            }
            
            return changed;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            boolean modified = false;
            Iterator<Map.Entry<K1, K2>> iterator = iterator();
            
            while (iterator.hasNext()) {
                Map.Entry<K1, K2> entry = iterator.next();
                
                if (!c.contains(entry)) {
                    modified = true;
                    iterator.remove();
                }
            }
            
            return modified;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean modified = false;
            
            for (Object o : c) {
                KeyPair<K1, K2> keyPair = (KeyPair<K1, K2>) o;
                
                if (remove(keyPair)) {
                    modified = true;
                }
            }
            
            return modified;
        }

        @Override
        public void clear() {
            BidirectionalHashMap.this.clear();
        }
    }
    
    /**
     * Makes the internal hash tables as small as possible without exceeding the
     * maximum load factor. If actual condensing is possible, the resulting new
     * hash tables will be of length of a power of two.
     */
    public void compact() {
        int newCapacity = MINIMUM_INITIAL_CAPACITY;
        
        while (size * maximumLoadFactor > newCapacity) {
            newCapacity <<= 1;
        }
        
        if (newCapacity == primaryHashTable.length) {
            // No compacting is possible.
            return;
        }
        
        
        PrimaryCollisionTreeNode<K1, K2>[] newPrimaryHashTable = 
                new PrimaryCollisionTreeNode[newCapacity];
        
        SecondaryCollisionTreeNode<K1, K2>[] newSecondaryHashTable =
                new SecondaryCollisionTreeNode[newCapacity];
        
        relink(newPrimaryHashTable, newSecondaryHashTable);
        this.moduloMask = newCapacity - 1;
        this.primaryHashTable = newPrimaryHashTable;
        this.secondaryHashTable = newSecondaryHashTable;
        // Do I need the following?
        ++modificationCount;
    }

    private static int roundToPowerOfTwo(int number) {
        int ret = 1;
        
        while (ret < number) {
            ret <<= 1;
        }
        
        return ret;
    }
    
    
    private static <K1 extends Comparable<? super K1>, 
                    K2 extends Comparable<? super K2>> 
        int getHeight(AbstractCollisionTreeNode<K1, K2> node) {
        return node != null ? node.height : -1;
    }
        
    private static <K1 extends Comparable<? super K1>, 
                    K2 extends Comparable<? super K2>> 
                AbstractCollisionTreeNode<K1, K2> 
        getMinimumNode(AbstractCollisionTreeNode<K1, K2> node) {
        while (node.leftChild != null) {
            node = node.leftChild;
        }
        
        return node;
    }
        
    private static <K1 extends Comparable<? super K1>,
                    K2 extends Comparable<? super K2>> 
                    AbstractCollisionTreeNode<K1, K2> 
            leftRotate(AbstractCollisionTreeNode<K1, K2> node1) {
        AbstractCollisionTreeNode<K1, K2> node2 = node1.rightChild;
        
        node2.parent = node1.parent;
        node1.parent = node2;
        node1.rightChild = node2.leftChild;
        node2.leftChild = node1;
        
        if (node1.rightChild != null) {
            node1.rightChild.parent = node1;
        }
        
        node1.height = Math.max(getHeight(node1.leftChild),
                                getHeight(node1.rightChild)) + 1;
        node2.height = Math.max(getHeight(node2.leftChild),
                                getHeight(node2.rightChild)) + 1;
        return node2;
    }
            
    private static <K1 extends Comparable<? super K1>, 
                    K2 extends Comparable<? super K2>> 
                    AbstractCollisionTreeNode<K1, K2> 
            rightRotate(AbstractCollisionTreeNode<K1, K2> node1) {
        AbstractCollisionTreeNode<K1, K2> node2 = node1.leftChild;
        
        node2.parent = node1.parent;
        node1.parent = node2;
        node1.leftChild = node2.rightChild;
        node2.rightChild = node1;
        
        if (node1.leftChild != null) {
            node1.leftChild.parent = node1;
        }
        
        node1.height = Math.max(getHeight(node1.leftChild),
                                getHeight(node1.rightChild)) + 1;
        node2.height = Math.max(getHeight(node2.leftChild),
                                getHeight(node2.rightChild)) + 1;
        return node2;
    }
            
    private static <K1 extends Comparable<? super K1>,
                    K2 extends Comparable<? super K2>> 
                    AbstractCollisionTreeNode<K1, K2>
            leftRightRotate(AbstractCollisionTreeNode<K1, K2> node1) {
        AbstractCollisionTreeNode<K1, K2> node2 = node1.leftChild;
        node1.leftChild = leftRotate(node2);
        return rightRotate(node1);
    }
            
    private static <K1 extends Comparable<? super K1>, 
                    K2 extends Comparable<? super K2>> 
                    AbstractCollisionTreeNode<K1, K2>
            rightLeftRotate(AbstractCollisionTreeNode<K1, K2> node1) {
        AbstractCollisionTreeNode<K1, K2> node2 = node1.rightChild;
        node1.rightChild = rightRotate(node2);
        return leftRotate(node1);
    }
            
    private static <K1 extends Comparable<? super K1>, 
                    K2 extends Comparable<? super K2>>
            void fixCollisionTreeAfterInsertion(
                AbstractCollisionTreeNode<K1, K2> node,
                AbstractCollisionTreeNode<K1, K2>[] hashTable,
                int bucketIndex) {
        fixCollisionTree(node, hashTable, bucketIndex, true);
    }
            
    private static <K1 extends Comparable<? super K1>, 
                    K2 extends Comparable<? super K2>>
            void fixCollisionTreeAfterDeletion(
                AbstractCollisionTreeNode<K1, K2> node,
                AbstractCollisionTreeNode<K1, K2>[] hashTable,
                int bucketIndex) {
        fixCollisionTree(node, hashTable, bucketIndex, false);
    }
            
    private static <K1 extends Comparable<? super K1>,
                    K2 extends Comparable<? super K2>> 
        void fixCollisionTree(AbstractCollisionTreeNode<K1, K2> node,
                              AbstractCollisionTreeNode<K1, K2>[] hashTable,
                              int bucketIndex,
                              boolean insertionMode) {
        AbstractCollisionTreeNode<K1, K2> grandParent;
        AbstractCollisionTreeNode<K1, K2> parent = node.parent;
        AbstractCollisionTreeNode<K1, K2> subtree;
        
        while (parent != null) {
            if (getHeight(parent.leftChild) ==
                    getHeight(parent.rightChild) + 2) {
                grandParent = parent.parent;
                
                if (getHeight(parent.leftChild.leftChild) >= 
                        getHeight(parent.leftChild.rightChild)) {
                    subtree = rightRotate(parent);
                } else {
                    subtree = leftRightRotate(parent);
                }
                
                if (grandParent == null) {
                    hashTable[bucketIndex] = subtree;
                } else if (grandParent.leftChild == parent) {
                    grandParent.leftChild = subtree;
                } else {
                    grandParent.rightChild = subtree;
                }
                
                if (grandParent != null) {
                    grandParent.height =
                            Math.max(getHeight(grandParent.leftChild),
                                     getHeight(grandParent.rightChild)) + 1;
                }
                
                if (insertionMode) {
                    return;
                }
            } else if (getHeight(parent.rightChild) == 
                    getHeight(parent.leftChild) + 2) {
                grandParent = parent.parent;
                
                if (getHeight(parent.rightChild.rightChild) >= 
                        getHeight(parent.rightChild.leftChild)) {
                    subtree = leftRotate(parent);
                } else {
                    subtree = rightLeftRotate(parent);
                }
                
                if (grandParent == null) {
                    hashTable[bucketIndex] = subtree;
                } else if (grandParent.leftChild == parent) {
                    grandParent.leftChild = subtree;
                } else {
                    grandParent.rightChild = subtree;
                }
                
                if (grandParent != null) {
                    grandParent.height =
                            Math.max(getHeight(grandParent.leftChild),
                                     getHeight(grandParent.rightChild)) + 1;
                }
                
                if (insertionMode) {
                    return;
                }
            }
            
            parent.height = 
                    Math.max(getHeight(parent.leftChild),
                             getHeight(parent.rightChild)) + 1;
            parent = parent.parent;
        }
    }
    
    private void addNewMapping(K1 primaryKey, K2 secondaryKey) {
        expandHashTablesIfNeeded();
        
        KeyPair<K1, K2> keyPair = new KeyPair<>(primaryKey, secondaryKey);
        
        PrimaryCollisionTreeNode<K1, K2> primaryCollisionTreeNode = 
                new PrimaryCollisionTreeNode<>();
        
        SecondaryCollisionTreeNode<K1, K2> secondaryCollisionTreeNode = 
                new SecondaryCollisionTreeNode<>();
        
        primaryCollisionTreeNode.keyPair = keyPair;
        secondaryCollisionTreeNode.keyPair = keyPair;
        
        int primaryCollisionTreeBucketIndex = 
                keyPair.primaryKeyHash & moduloMask;
        
        int secondaryCollisionTreeBucketIndex =
                keyPair.secondaryKeyHash & moduloMask;
        
        linkCollisionTreeNodeToPrimaryTable(primaryCollisionTreeNode,
                                            primaryHashTable, 
                                            primaryCollisionTreeBucketIndex);
        
        linkCollisionTreeNodeToSecondaryTable(
                secondaryCollisionTreeNode,
                secondaryHashTable,
                secondaryCollisionTreeBucketIndex);
        
        linkPrimaryCollisionTreeNodeIntoIterationChain(
                primaryCollisionTreeNode);
    }
    
    private void linkPrimaryCollisionTreeNodeIntoIterationChain(
            PrimaryCollisionTreeNode<K1, K2> node) {
        if (size == 0) {
            iterationListHead = node;
            iterationListTail = node;
        } else {
            iterationListTail.down = node;
            node.up = iterationListTail;
            iterationListTail = node;
        }
    }
    
    private void unlinkPrimaryCollisionTreeNodeFromIterationChain(
            PrimaryCollisionTreeNode<K1, K2> node) {
        if (node.up != null) {
            node.up.down = node.down;
        } else {
            iterationListHead = node.down;
        }
        
        if (node.down != null) {
            node.down.up = node.up;
        } else {
            iterationListTail = iterationListTail.up;
            
            if (iterationListTail != null) {
                iterationListTail.down = null;
            }
        }
    }
    
    private void unlinkCollisionTreeNode(
                AbstractCollisionTreeNode<K1, K2> node,
                AbstractCollisionTreeNode<K1, K2>[] hashTable,
                int bucketIndex) {
        if (node.leftChild == null && node.rightChild == null) {
            unlinkCollisionTreeNodeWithNoChildren(node, hashTable, bucketIndex);
        } else if (node.leftChild != null && node.rightChild != null) {
            unlinkCollisionTreeNodeWithBothChildren(node, 
                                                    hashTable, 
                                                    bucketIndex);
        } else {
            unlinkCollisionTreeNodeWithOneChild(node, hashTable, bucketIndex);
        }
    }
        
    private void unlinkCollisionTreeNodeWithNoChildren(
                AbstractCollisionTreeNode<K1, K2> node,
                AbstractCollisionTreeNode<K1, K2>[] hashTable,
                int bucketIndex) {
        if (node.parent == null) {
            hashTable[bucketIndex] = null;
            return;
        }
        
        if (node.parent.leftChild == node) {
            node.parent.leftChild = null;
        } else {
            node.parent.rightChild = null;
        }
        
        fixCollisionTreeAfterDeletion(node.parent, hashTable, bucketIndex);
    }
        
    private void unlinkCollisionTreeNodeWithOneChild(
                AbstractCollisionTreeNode<K1, K2> node,
                AbstractCollisionTreeNode<K1, K2>[] hashTable,
                int bucketIndex) {
        AbstractCollisionTreeNode<K1, K2> child;
        
        if (node.leftChild != null) {
            child = node.leftChild;
        } else {
            child = node.rightChild;
        }
        
        AbstractCollisionTreeNode<K1, K2> parent = node.parent;
        child.parent = parent;
        
        if (parent == null) {
            hashTable[bucketIndex] = child;
            
            if (node.leftChild == child) {
                node.leftChild = null;
            } else {
                node.rightChild = null;
            }
            
            return;
        }
        
        if (node == parent.leftChild) {
            parent.leftChild = child;
        } else {
            parent.rightChild = child;
        }
        
        fixCollisionTreeAfterDeletion(node, hashTable, bucketIndex);
    }
        
    private void unlinkCollisionTreeNodeWithBothChildren(
                AbstractCollisionTreeNode<K1, K2> node,
                AbstractCollisionTreeNode<K1, K2>[] hashTable,
                int bucketIndex) {
        AbstractCollisionTreeNode<K1, K2> successor =
                getMinimumNode(node.rightChild);
        
        node.keyPair = successor.keyPair;
        
        AbstractCollisionTreeNode<K1, K2> parent = successor.parent;
        AbstractCollisionTreeNode<K1, K2> child = successor.rightChild;
        
        if (parent.leftChild == successor) {
            parent.leftChild = child;
        } else {
            parent.rightChild = child;
        }
        
        if (child != null) {
            child.parent = parent;
        }
        
        fixCollisionTreeAfterDeletion(successor, hashTable, bucketIndex);
    }
        
    private void linkCollisionTreeNodeToPrimaryTable(
                    AbstractCollisionTreeNode<K1, K2> node,
                    AbstractCollisionTreeNode<K1, K2>[] hashTable,
                    int bucketIndex) {
        if (hashTable[bucketIndex] == null) {
            hashTable[bucketIndex] = node;
            return;
        }
        
        AbstractCollisionTreeNode<K1, K2> currentNode = hashTable[bucketIndex];
        AbstractCollisionTreeNode<K1, K2> parentOfCurrentNode = 
                currentNode.parent;
        
        while (currentNode != null) {
            parentOfCurrentNode = currentNode;
            
            int cmp =
                    node.keyPair.primaryKey
                            .compareTo(currentNode.keyPair.primaryKey);
            
            if (cmp < 0) {
                currentNode = currentNode.leftChild;
            } else if (cmp > 0) {
                currentNode = currentNode.rightChild;
            } else {
                throw new IllegalStateException("This should not be thrown.");
            }
        }
        
        node.parent = parentOfCurrentNode;
        
        if (node.keyPair.primaryKey
                .compareTo(parentOfCurrentNode.keyPair.primaryKey) < 0) {
            parentOfCurrentNode.leftChild = node;
        } else {
            parentOfCurrentNode.rightChild = node;
        }
        
        fixCollisionTreeAfterInsertion(parentOfCurrentNode, 
                                       hashTable,
                                       bucketIndex);
    }
        
    private void linkCollisionTreeNodeToSecondaryTable(
                    AbstractCollisionTreeNode<K1, K2> node,
                    AbstractCollisionTreeNode<K1, K2>[] hashTable,
                    int bucketIndex) {
        if (hashTable[bucketIndex] == null) {
            hashTable[bucketIndex] = node;
            // Remove null
            node.leftChild = null;
            node.rightChild = null;
            return;
        }
        
        AbstractCollisionTreeNode<K1, K2> currentNode = hashTable[bucketIndex];
        AbstractCollisionTreeNode<K1, K2> parentOfCurrentNode = null;
        
        while (currentNode != null) {
            parentOfCurrentNode = currentNode;
            
            int cmp =
                    node.keyPair.secondaryKey
                            .compareTo(currentNode.keyPair.secondaryKey);
            
            if (cmp < 0) {
                currentNode = currentNode.leftChild;
            } else if (cmp > 0) {
                currentNode = currentNode.rightChild;
            } else {
                throw new IllegalStateException("This should not be thrown.");
            }
        }
        
        node.parent = parentOfCurrentNode;
        
        if (node.keyPair.secondaryKey
                .compareTo(parentOfCurrentNode.keyPair.secondaryKey) < 0) {
            parentOfCurrentNode.leftChild = node;
        } else {
            parentOfCurrentNode.rightChild = node;
        }
        
        fixCollisionTreeAfterInsertion(node, 
                                       hashTable, 
                                       bucketIndex);
    }
    
    private void relink(
                PrimaryCollisionTreeNode<K1, K2>[] newPrimaryHashTable,
                SecondaryCollisionTreeNode<K1, K2>[] newSecondaryHashTable) {
        PrimaryCollisionTreeNode<K1, K2> finger = iterationListHead;
        
        // We expect 'newPrimaryHashTable.length' to be a power of two!!!
        int newModuloMask = newPrimaryHashTable.length - 1;
        
        while (finger != null) {
            int primaryKeyHash = finger.keyPair.primaryKeyHash;
            int secondaryKeyHash = finger.keyPair.secondaryKeyHash;
            int primaryCollisionTreeBucketIndex = primaryKeyHash & moduloMask;
            int secondaryCollisionTreeBucketIndex = secondaryKeyHash 
                                                  & moduloMask;
            
            // Unlink the pair of collision tree nodes from their collision
            // trees in current hash tables:
            AbstractCollisionTreeNode<K1, K2> oppositeNode = 
                    getSecondaryTreeNodeViaPrimaryTreeNode(finger);
            
            unlinkCollisionTreeNode(finger,
                                    primaryHashTable,
                                    primaryCollisionTreeBucketIndex);
            
            unlinkCollisionTreeNode(oppositeNode,
                                    secondaryHashTable,
                                    secondaryCollisionTreeBucketIndex);
            
            int newPrimaryCollisionTreeBucketIndex = primaryKeyHash 
                                                   & newModuloMask;
            
            int newSecondaryCollisionTreeBucketIndex = secondaryKeyHash
                                                     & newModuloMask;
            
            // Link the pair of collision tree nodes to the argument tables:
            linkCollisionTreeNodeToPrimaryTable(
                    finger, 
                    newPrimaryHashTable,
                    newPrimaryCollisionTreeBucketIndex);
            
            linkCollisionTreeNodeToSecondaryTable(
                    oppositeNode,
                    newSecondaryHashTable,
                    newSecondaryCollisionTreeBucketIndex);
            
            finger = finger.down;
        }
    }
    
    private void expandHashTablesIfNeeded() {
        if (size <= maximumLoadFactor * primaryHashTable.length) {
            return;
        }
        
        int newCapacity = primaryHashTable.length << 1;
        
        PrimaryCollisionTreeNode<K1, K2>[] newPrimaryHashTable = 
                new PrimaryCollisionTreeNode[newCapacity];
        
        SecondaryCollisionTreeNode<K1, K2>[] newSecondaryHashTable =
                new SecondaryCollisionTreeNode[newCapacity];
        
        relink(newPrimaryHashTable, newSecondaryHashTable);
        this.moduloMask = newCapacity - 1;
        this.primaryHashTable = newPrimaryHashTable;
        this.secondaryHashTable = newSecondaryHashTable;
    }
    
    private AbstractCollisionTreeNode<K1, K2> 
        getSecondaryTreeNodeViaPrimaryTreeNode(
                PrimaryCollisionTreeNode<K1, K2> primaryCollisionTreeNode) {
        int secondaryNodeHash = primaryCollisionTreeNode.keyPair
                                                        .secondaryKeyHash;
        int secondaryCollisionTreeBucketIndex = secondaryNodeHash & moduloMask;
        
        AbstractCollisionTreeNode<K1, K2> secondaryCollisionTreeNode = 
                secondaryHashTable[secondaryCollisionTreeBucketIndex];
        
        K2 targetSecondaryKey = primaryCollisionTreeNode.keyPair.secondaryKey;
        
        while (secondaryCollisionTreeNode != null) {
            if (secondaryCollisionTreeNode.keyPair == 
                    primaryCollisionTreeNode.keyPair) {
                return secondaryCollisionTreeNode;
            }
            
            int cmp = targetSecondaryKey
                    .compareTo(secondaryCollisionTreeNode.keyPair.secondaryKey);
            
            if (cmp < 0) {
                secondaryCollisionTreeNode = 
                        secondaryCollisionTreeNode.leftChild;
            } else {
                secondaryCollisionTreeNode =
                        secondaryCollisionTreeNode.rightChild;
            }
        }
        
        throw new IllegalStateException(
                "Failed to find a secondary node given an existing primary " +
                "node.");
    }
        
    private AbstractCollisionTreeNode<K1, K2>
    getPrimaryTreeNodeViaSecondaryTreeNode(
            SecondaryCollisionTreeNode<K1, K2> secondaryCollisionTreeNode) {
        int primaryNodeHash = secondaryCollisionTreeNode.keyPair.primaryKeyHash;
        int primaryCollisionTreeBucketIndex = primaryNodeHash & moduloMask;
        
        AbstractCollisionTreeNode<K1, K2> primaryCollisionTreeNode =
                primaryHashTable[primaryCollisionTreeBucketIndex];
        
        K1 targetPrimaryKey = secondaryCollisionTreeNode.keyPair.primaryKey;
        
        while (primaryCollisionTreeNode != null) {
            if (primaryCollisionTreeNode.keyPair == 
                    secondaryCollisionTreeNode.keyPair) {
                return primaryCollisionTreeNode;
            }
            
            int cmp = targetPrimaryKey
                    .compareTo(primaryCollisionTreeNode.keyPair.primaryKey);
            
            if (cmp < 0) {
                primaryCollisionTreeNode = 
                        primaryCollisionTreeNode.leftChild;
            } else {
                primaryCollisionTreeNode =
                        primaryCollisionTreeNode.rightChild;
            }
        }
        
        throw new IllegalStateException(
                "Failed to find a primary node given an existing secondary " +
                "node.");
    }
        
    private void checkModificationCount(int expectedModificationCount) {
        if (modificationCount != expectedModificationCount) {
            throw new ConcurrentModificationException(
                    "This BidirectionalHashMap was modified during iteration!");
        }
    }
}
