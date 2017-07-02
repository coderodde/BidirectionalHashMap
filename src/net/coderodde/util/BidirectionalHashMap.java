package net.coderodde.util;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BidirectionalHashMap<K1 extends Comparable<? super K1>, 
                                        K2 extends Comparable<? super K2>>
        implements Map<K1, K2> {

    /**
     * The class for holding primary and secondary keys and their respective
     * hash values.
     * 
     * @param <K1> the type of the primary keys.
     * @param <K2> the type of the secondary keys.
     */
    private static final class KeyPair<K1, K2> implements Map.Entry<K1, K2> {
        
        /**
         * The primary key.
         */
        K1 primaryKey;
        
        /**
         * The secondary key.
         */
        K2 secondaryKey;
        
        /**
         * The hash value of the primary key. We cache this in order to have a
         * slight performance advantage when dealing with, say, strings or other
         * containers.
         */
        int primaryKeyHash;
        
        /**
         * The hash value of the secondary key.
         */
        int secondaryKeyHash;

        KeyPair(K1 primaryKey, K2 secondaryKey) {
            this.primaryKey = Objects.requireNonNull(primaryKey, 
                                      "Null keys are not allowed.");
            this.secondaryKey = Objects.requireNonNull(secondaryKey,
                                      "Null keys are not allowed.");
            
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
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public boolean containsValue(Object value) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public K1 get(Object key) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public K1 put(K2 key, K1 value) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public K1 remove(Object key) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void putAll(Map<? extends K2, ? extends K1> m) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Set<K2> keySet() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Collection<K1> values() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Set<Entry<K2, K1>> entrySet() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
            return oldValue;
        } else {
            addNewMapping(key, value);
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
        unlinkCollisionTreeNode(node,
                                primaryHashTable, 
                                primaryCollisionTreeBucketIndex);
        --size;
        return oldValue;
    }

    @Override
    public void putAll(Map<? extends K1, ? extends K2> m) {
        
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Set<K1> keySet() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Collection<K2> values() {
        throw new UnsupportedOperationException("values() not implemented.");
    }
    
    @Override
    public Set<Entry<K1, K2>> entrySet() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        KeyPair<K1, K2> keyPair = new KeyPair<>(primaryKey, secondaryKey);
        
        PrimaryCollisionTreeNode<K1, K2> primaryCollisionTreeNode = 
                new PrimaryCollisionTreeNode<>();
        
        SecondaryCollisionTreeNode<K1, K2> secondaryCollisionTreeNode = 
                new SecondaryCollisionTreeNode<>();
        
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
    
    private static <K1 extends Comparable<? super K1>, 
                    K2 extends Comparable<? super K2>> 
        void unlinkCollisionTreeNode(
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
        
    private static <K1 extends Comparable<? super K1>, 
                    K2 extends Comparable<? super K2>> 
        void unlinkCollisionTreeNodeWithNoChildren(
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
        
    private static <K1 extends Comparable<? super K1>, 
                    K2 extends Comparable<? super K2>>
        void unlinkCollisionTreeNodeWithOneChild(
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
            return;
        }
        
        if (node == parent.leftChild) {
            parent.leftChild = child;
        } else {
            parent.rightChild = child;
        }
        
        fixCollisionTreeAfterDeletion(parent, hashTable, bucketIndex);
    }
        
    private static <K1 extends Comparable<? super K1>, 
                    K2 extends Comparable<? super K2>>
        void unlinkCollisionTreeNodeWithBothChildren(
                AbstractCollisionTreeNode<K1, K2> node,
                AbstractCollisionTreeNode<K1, K2>[] hashTable,
                int bucketIndex) {
        AbstractCollisionTreeNode<K1, K2> successor =
                getMinimumNode(node.rightChild);
        
        node.keyPair = successor.keyPair;
        
        AbstractCollisionTreeNode<K1, K2> parent = successor.parent;
        AbstractCollisionTreeNode<K1, K2> child = successor.rightChild;
        
        parent.leftChild = child;
        
        if (child != null) {
            child.parent = parent;
        }
        
        fixCollisionTreeAfterDeletion(parent, hashTable, bucketIndex);
    }
        
    private static <K1 extends Comparable<? super K1>, 
                    K2 extends Comparable<? super K2>> 
            void linkCollisionTreeNodeToPrimaryTable(
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
    }
        
    private static <K1 extends Comparable<? super K1>, 
                    K2 extends Comparable<? super K2>> 
            void linkCollisionTreeNodeToSecondaryTable(
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
    }
    
}
