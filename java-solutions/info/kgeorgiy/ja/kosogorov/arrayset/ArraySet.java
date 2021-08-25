package info.kgeorgiy.ja.kosogorov.arrayset;

import java.util.*;

public class ArraySet<E> extends AbstractSet<E> implements NavigableSet<E> {
    private final ReversibleList<E> list;
    private final Comparator<? super E> comparator;

    public ArraySet() {
        this.list = new ReversibleList<>(false);
        comparator = null;
    }

    private ArraySet(Comparator<? super E> comparator) {
        this.list = new ReversibleList<>(true);
        this.comparator = comparator;
    }

    private ArraySet(ReversibleList<E> list, Comparator<? super E> comparator) {
        this.list = list;
        this.comparator = comparator;
    }

    public ArraySet(Collection<? extends E> collection) {
        this(collection, null);
    }

    public ArraySet(Collection<? extends E> collection, Comparator<? super E> comparator) {
        Set<E> treeSet = new TreeSet<>(comparator);
        treeSet.addAll(collection);
        this.list = new ReversibleList<>(new ArrayList<>(treeSet), false);
        this.comparator = comparator;
    }

    private boolean inRange(int i) {
        return i >= 0 && i < size();
    }

    private int getPosition(E element, int foundOffset, int notFoundOffset) {
        int res = Collections.binarySearch(list, element, comparator);
        return res < 0 ? -res - 1 + notFoundOffset : res + foundOffset;
    }

    E getNullable(int index) {
        return inRange(index)? list.get(index) : null;
    }

    @Override
    public E lower(E e) {
        return getNullable(getPosition(e, -1, -1));
    }

    @Override
    public E floor(E e) {
        return getNullable(getPosition(e, 0, -1));
    }

    @Override
    public E ceiling(E e) {
        return getNullable(getPosition(e, 0, 0));
    }

    @Override
    public E higher(E e) {
        return getNullable(getPosition(e, 1, 0));
    }

    @Override
    public E pollFirst() {
        throw new UnsupportedOperationException();
    }

    @Override
    public E pollLast() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean contains(Object o) {
        return Collections.binarySearch(list, (E)o, comparator) >= 0;
    }

    @Override
    public Iterator<E> iterator() {
        return Collections.unmodifiableList(list).iterator();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends E> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public NavigableSet<E> descendingSet() {
        return new ArraySet<>(new ReversibleList<>(list.list, !list.isReversed), Collections.reverseOrder(comparator));
    }

    @Override
    public Iterator<E> descendingIterator() {
        return descendingSet().iterator();
    }

    private NavigableSet<E> subSetIndexed(int fromIndex, int toIndex) {
        return new ArraySet<>((!inRange(fromIndex) || !inRange(toIndex - 1) || fromIndex >= toIndex?
                new ReversibleList<>(list.isReversed) :
                new ReversibleList<>(list.list.subList(fromIndex, toIndex), list.isReversed)), comparator);
    }

    @SuppressWarnings("unchecked")
    @Override
    public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
        int compare = comparator == null?
                ((Comparable<E>)fromElement).compareTo(toElement) :
                comparator.compare(fromElement, toElement);
        if (compare > 0) {
            throw new IllegalArgumentException();
        }
        int fromIndex = getPosition(fromElement, fromInclusive? 0 : 1, 0);
        int toIndex = getPosition(toElement, toInclusive? 0 : -1, -1) + 1;
        return subSetIndexed(fromIndex, toIndex);
    }

    @Override
    public NavigableSet<E> headSet(E toElement, boolean inclusive) {
        if (isEmpty()) {
            return new ArraySet<>(comparator);
        }
        int index = getPosition(toElement, inclusive? 0 : -1, -1) + 1;
        return subSetIndexed(0, index);
    }

    @Override
    public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
        if (isEmpty()) {
            return new ArraySet<>(comparator);
        }
        int index = getPosition(fromElement, inclusive? 0 : 1, 0);
        return subSetIndexed(index, size());
    }

    @Override
    public Comparator<? super E> comparator() {
        return comparator;
    }

    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
        return subSet(fromElement, true, toElement, false);
    }

    @Override
    public SortedSet<E> headSet(E toElement) {
        return headSet(toElement, false);
    }

    @Override
    public SortedSet<E> tailSet(E fromElement) {
        return tailSet(fromElement, true);
    }

    @Override
    public E first() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return list.get(0);
    }

    @Override
    public E last() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return list.get(size() - 1);
    }

    private static class ReversibleList<E> extends AbstractList<E> implements RandomAccess {
        private final boolean isReversed;
        private final List<E> list;

        public ReversibleList(boolean isReversed) {
            this.isReversed = isReversed;
            this.list = new ArrayList<>();
        }

        public ReversibleList(List<E> list, boolean isReversed) {
            this.isReversed = isReversed;
            this.list = list;
        }

        @Override
        public int size() {
            return list.size();
        }

        @Override
        public E get(int i) {
            return isReversed? list.get(size() - i - 1) : list.get(i);
        }
    }
}


