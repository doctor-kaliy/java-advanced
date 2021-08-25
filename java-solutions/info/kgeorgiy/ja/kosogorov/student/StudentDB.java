package info.kgeorgiy.ja.kosogorov.student;

import info.kgeorgiy.java.advanced.student.*;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StudentDB implements AdvancedQuery {

    private static class Comparators {
        private static final Comparator<Student> FULL_NAME =
                Comparator.comparing(Student::getLastName, Comparator.reverseOrder())
                        .thenComparing(Student::getFirstName, Comparator.reverseOrder())
                        .thenComparing(Student::getId);

        private static final Comparator<Map.Entry<GroupName, Integer>> GROUP_INT_REVERSED =
                Map.Entry.<GroupName, Integer>comparingByValue()
                        .thenComparing(Map.Entry.<GroupName, Integer>comparingByKey().reversed());

        private static final Comparator<Map.Entry<GroupName, Long>> GROUP_LONG =
                Map.Entry.<GroupName, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey());
    }

    private <T, R> Optional<R> getMax(
            Stream<? extends T> stream,
            Comparator<? super T> comparator,
            Function<? super T, R> extractor
    ) {
        return stream.max(comparator).map(extractor);
    }

    private <T, R> Optional<R> getMax(
            Collection<? extends T> collection,
            Comparator<? super T> comparator,
            Function<? super T, R> extractor
    ) {
        return getMax(collection.stream(), comparator, extractor);
    }

    private <T, R> Stream<T> getGroupedImpl(
            Collection<Student> students,
            Function<Map.Entry<R, List<Student>>, T> mapper,
            Function<Student, R> groupingFunction,
            Comparator<T> sortComparator
    ) {
        return students.stream().collect(Collectors.groupingBy(groupingFunction))
                .entrySet().stream().map(mapper)
                .sorted(sortComparator);
    }

    private Stream<Group> getGroupsImpl(
            Collection<Student> students,
            Function<Collection<Student>, List<Student>> mapper
    ) {
        return getGroupedImpl(
                students,
                (Map.Entry<GroupName, List<Student>> entry) -> new Group(entry.getKey(), mapper.apply(entry.getValue())),
                Student::getGroup,
                Comparator.comparing(Group::getName)
        );
    }

    private List<Group> getGroupsImplList(
            Collection<Student> students,
            Function<Collection<Student>, List<Student>> mapper
    ) {
        return getGroupsImpl(students, mapper).collect(Collectors.toList());
    }

    @Override
    public List<Group> getGroupsByName(Collection<Student> students) {
        return getGroupsImplList(students, this::sortStudentsByName);
    }

    @Override
    public List<Group> getGroupsById(Collection<Student> students) {
        return getGroupsImplList(students, this::sortStudentsById);
    }

    private <T, R> Optional<T> getLargestImpl(
            Collection<Student> students,
            Function <Student, T> groupingFunction,
            Collector<Student, ?, R> collector,
            Comparator<Map.Entry<T, R>> comparator
    ) {
        return getMax(
                students.stream().collect(Collectors.groupingBy(groupingFunction, collector)).entrySet().stream(),
                comparator,
                Map.Entry::getKey
        );
    }

    @Override
    public GroupName getLargestGroup(Collection<Student> students) {
        return getLargestImpl(
                students,
                Student::getGroup,
                Collectors.counting(),
                Comparators.GROUP_LONG
        ).orElse(null);
    }

    @Override
    public GroupName getLargestGroupFirstName(Collection<Student> students) {
        return getLargestImpl(
                students,
                Student::getGroup,
                Collectors.collectingAndThen(Collectors.mapping(Student::getFirstName, Collectors.toSet()), Set::size),
                Comparators.GROUP_INT_REVERSED
        ).orElse(null);
    }

    private <T> Stream<T> getFieldsImpl(List<Student> students, Function<Student, ? extends T> getter) {
        return students.stream().map(getter);
    }

    private <T> List<T> getFields(List<Student> students, Function<Student, ? extends T> getter) {
        return getFieldsImpl(students, getter).collect(Collectors.toList());
    }

    @Override
    public List<String> getFirstNames(List<Student> students) {
        return getFields(students, Student::getFirstName);
    }

    @Override
    public List<String> getLastNames(List<Student> students) {
        return getFields(students, Student::getLastName);
    }

    @Override
    public List<GroupName> getGroups(List<Student> students) {
        return getFields(students, Student::getGroup);
    }

    @Override
    public List<String> getFullNames(List<Student> students) {
        return getFields(students, s -> s.getFirstName() + " " + s.getLastName());
    }

    @Override
    public Set<String> getDistinctFirstNames(List<Student> students) {
        return getFieldsImpl(students, Student::getFirstName).collect(Collectors.toCollection(TreeSet<String>::new));
    }

    @Override
    public String getMaxStudentFirstName(List<Student> students) {
        return getMax(students, Comparator.naturalOrder(), Student::getFirstName).orElse("");
    }

    private Stream<Student> sortByImpl(Collection<Student> students, Comparator<? super Student> comparator) {
        return students.stream().sorted(comparator);
    }

    private List<Student> sortByImplList(Collection<Student> students, Comparator<? super Student> comparator) {
        return sortByImpl(students, comparator).collect(Collectors.toList());
    }

    @Override
    public List<Student> sortStudentsById(Collection<Student> students) {
        return sortByImplList(students, Comparator.naturalOrder());
    }

    @Override
    public List<Student> sortStudentsByName(Collection<Student> students) {
        return sortByImplList(students, Comparators.FULL_NAME);
    }

    private <T> Predicate<Student> getEqualsPredicate(Function<Student, ? extends T> getter, T value) {
        return student -> getter.apply(student).equals(value);
    }

    private Stream<Student> findByImpl(Collection<Student> students, Predicate<Student> predicate) {
        return sortByImpl(students, Comparators.FULL_NAME).filter(predicate);
    }

    private List<Student> findByImplList(Collection<Student> students, Predicate<Student> predicate) {
        return findByImpl(students, predicate).collect(Collectors.toList());
    }

    @Override
    public List<Student> findStudentsByFirstName(Collection<Student> students, String name) {
        return findByImplList(students, getEqualsPredicate(Student::getFirstName, name));
    }

    @Override
    public List<Student> findStudentsByLastName(Collection<Student> students, String name) {
        return findByImplList(students, getEqualsPredicate(Student::getLastName, name));
    }

    @Override
    public List<Student> findStudentsByGroup(Collection<Student> students, GroupName group) {
        return findByImplList(students, getEqualsPredicate(Student::getGroup, group));
    }

    @Override
    public Map<String, String> findStudentNamesByGroup(Collection<Student> students, GroupName group) {
        return findByImpl(students, getEqualsPredicate(Student::getGroup, group))
                .collect(
                        Collectors.toMap(Student::getLastName, Student::getFirstName, BinaryOperator.minBy(String::compareTo))
                );
    }

    @Override
    public String getMostPopularName(Collection<Student> students) {
        return getLargestImpl(
                students,
                Student::getFirstName,
                Collectors.collectingAndThen(Collectors.mapping(Student::getGroup, Collectors.toSet()), Set::size),
                Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry::getKey)
        ).orElse("");
    }

    private <T> List<T> getFromList(ArrayList<Student> students, int[] indices, Function<Student, T> getter) {
        return Arrays.stream(indices).mapToObj(ind -> getter.apply(students.get(ind))).collect(Collectors.toList());
    }

    private <T> List<T> getListImpl(Collection<Student> students, int[] indices, Function<Student, T> getter) {
        return getFromList(new ArrayList<>(students), indices, getter);
    }

    @Override
    public List<String> getFirstNames(Collection<Student> students, int[] indices) {
        return getListImpl(students, indices, Student::getFirstName);
    }

    @Override
    public List<String> getLastNames(Collection<Student> students, int[] indices) {
        return getListImpl(students, indices, Student::getLastName);
    }

    @Override
    public List<GroupName> getGroups(Collection<Student> students, int[] indices) {
        return getListImpl(students, indices, Student::getGroup);
    }

    @Override
    public List<String> getFullNames(Collection<Student> students, int[] indices) {
        return getListImpl(students, indices, s -> s.getFirstName() + " " + s.getLastName());
    }
}
