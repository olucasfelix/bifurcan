package io.lacuna.bifurcan;

import io.lacuna.bifurcan.utils.Iterators;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.IntStream;

/**
 * @author ztellman
 */
public class Sets {

  public static final ISet EMPTY = new ISet() {
    @Override
    public boolean contains(Object value) {
      return false;
    }

    @Override
    public long size() {
      return 0;
    }

    @Override
    public IList elements() {
      return Lists.EMPTY;
    }

    @Override
    public ISet add(Object value) {
      return new Set().add(value);
    }

    @Override
    public ISet remove(Object value) {
      return this;
    }

    @Override
    public ISet forked() {
      return new Set().forked();
    }

    @Override
    public ISet linear() {
      return new Set().linear();
    }
  };

  public static class Proxy<V> implements ISet<V> {

    private Set<V> canonical;
    private ISet<V> base, added, removed;
    private boolean linear;

    public Proxy(ISet<V> base) {
      this(base, Sets.EMPTY, Sets.EMPTY, false);
    }

    private Proxy(ISet<V> base, ISet<V> added, ISet<V> removed, boolean linear) {
      this.base = base;
      this.added = added;
      this.removed = removed;
      this.linear = linear;
    }

    private void canonicalize() {
      if (canonical != null) {
        canonical = Set.from(base).union(added).difference(removed);
        base = null;
        added = null;
        removed = null;
      }
    }

    private boolean altered() {
      return removed.size() > 0;
    }

    @Override
    public synchronized boolean contains(V value) {
      return !removed.contains(value) && (base.contains(value) || added.contains(value));
    }

    @Override
    public long size() {
      return (base.size() + added.size()) - removed.size();
    }

    @Override
    public IList<V> elements() {
      if (!altered()) {
        return Lists.concat(added.elements(), base.elements());
      } else {
        canonicalize();
        return canonical.elements();
      }
    }

    @Override
    public synchronized ISet<V> add(V value) {
      if (canonical != null) {
        return canonical.add(value);
      } else {
        ISet<V> removedPrime = removed.remove(value);
        ISet<V> addedPrime = added;
        if (!base.contains(value)) {
          addedPrime = added.add(value);
        }
        return linear ? this : new Proxy<V>(base, addedPrime, removedPrime, false);
      }
    }

    @Override
    public synchronized ISet<V> remove(V value) {
      if (canonical != null) {
        return canonical.remove(value);
      } else {
        ISet<V> removedPrime = removed.add(value);
        ISet<V> addedPrime = added.remove(value);
        return linear ? this : new Proxy<V>(base, addedPrime, removedPrime, false);
      }
    }

    @Override
    public synchronized Iterator<V> iterator() {
      if (canonical != null) {
        return canonical.iterator();
      } else if (!altered()) {
        return Iterators.concat(added.iterator(), base.iterator());
      } else {
        return Iterators.concat(added.iterator(), Iterators.filter(base.iterator(), v -> !removed.contains(v)));
      }
    }

    @Override
    public ISet<V> forked() {
      if (canonical != null) {
        return canonical.forked();
      } else {
        return new Proxy<V>(added.forked(), removed.forked(), base, false);
      }
    }

    @Override
    public ISet<V> linear() {
      if (canonical != null) {
        return canonical.linear();
      } else {
        return new Proxy<V>(added.linear(), removed.linear(), base, true);
      }
    }
  }

  public static <V> long hash(ISet<V> s) {
    return hash(s, Objects::hashCode, (a, b) -> a + b);
  }

  public static <V> long hash(ISet<V> set, ToLongFunction<V> hash, LongBinaryOperator combiner) {
    return set.elements().stream().mapToLong(hash).reduce(combiner).orElse(0);
  }

  public static <V> boolean equals(ISet<V> a, ISet<V> b) {
    if (a.size() != b.size()) {
      return false;
    }
    return a.elements().stream().allMatch(b::contains);
  }

  public static <V> ISet<V> difference(ISet<V> a, ISet<V> b) {
    for (V e : b) {
      a = a.remove(e);
    }
    return a;
  }

  public static <V> ISet<V> union(ISet<V> a, ISet<V> b) {
    for (V e : b) {
      a = a.add(e);
    }
    return a;
  }

  public static <V> ISet<V> intersection(ISet<V> accumulator, ISet<V> a, ISet<V> b) {
    if (b.size() < a.size()) {
      return intersection(accumulator, b, a);
    }
    for (V e : a) {
      if (b.contains(e)) {
        accumulator = accumulator.add(e);
      }
    }
    return accumulator;
  }

  public static <V> java.util.Set<V> toSet(IList<V> elements, Predicate<V> contains) {
    return new java.util.Set<V>() {
      @Override
      public int size() {
        return (int) elements.size();
      }

      @Override
      public boolean isEmpty() {
        return elements.size() == 0;
      }

      @Override
      public boolean contains(Object o) {
        return contains.test((V) o);
      }

      @Override
      public Iterator<V> iterator() {
        return elements.iterator();
      }

      @Override
      public Object[] toArray() {
        return elements.toArray();
      }

      @Override
      public <T> T[] toArray(T[] a) {
        T[] ary = (T[]) Array.newInstance(a.getClass().getComponentType(), size());
        IntStream.range(0, size()).forEach(i -> ary[i] = (T) elements.nth(i));
        return ary;
      }

      @Override
      public boolean add(V v) {
        return false;
      }

      @Override
      public boolean remove(Object o) {
        return false;
      }

      @Override
      public boolean containsAll(Collection<?> c) {
        return c.stream().allMatch(e -> contains(e));
      }

      @Override
      public boolean addAll(Collection<? extends V> c) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void clear() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public static <V> ISet<V> from(IList<V> elements, Predicate<V> contains) {
    return from(elements, contains, elements::iterator);
  }

  public static <V> ISet<V> from(IList<V> elements, Predicate<V> contains, Supplier<Iterator<V>> iterator) {
    return new ISet<V>() {
      @Override
      public boolean contains(V value) {
        return contains.test(value);
      }

      @Override
      public long size() {
        return elements.size();
      }

      @Override
      public IList<V> elements() {
        return elements;
      }

      @Override
      public Iterator<V> iterator() {
        return iterator.get();
      }
    };
  }

  public static <V> ISet<V> from(java.util.Set<V> s) {
    return new ISet<V>() {
      @Override
      public boolean contains(V value) {
        return s.contains(value);
      }

      @Override
      public long size() {
        return s.size();
      }

      @Override
      public Iterator<V> iterator() {
        return s.iterator();
      }

      @Override
      public IList<V> elements() {
        return (IList<V>) Lists.from(s.toArray());
      }
    };
  }

  public static <V> String toString(ISet<V> set) {
    return toString(set, Objects::toString);
  }

  public static <V> String toString(ISet<V> set, Function<V, String> elementPrinter) {
    StringBuilder sb = new StringBuilder("{");

    Iterator<V> it = set.elements().iterator();
    while (it.hasNext()) {
      sb.append(elementPrinter.apply(it.next()));
      if (it.hasNext()) {
        sb.append(", ");
      }
    }
    sb.append("}");

    return sb.toString();
  }

  public static <V> Collector<V, LinearSet<V>, LinearSet<V>> linearCollector() {
    return new Collector<V, LinearSet<V>, LinearSet<V>>() {
      @Override
      public Supplier<LinearSet<V>> supplier() {
        return LinearSet::new;
      }

      @Override
      public BiConsumer<LinearSet<V>, V> accumulator() {
        return LinearSet::add;
      }

      @Override
      public BinaryOperator<LinearSet<V>> combiner() {
        return LinearSet::union;
      }

      @Override
      public Function<LinearSet<V>, LinearSet<V>> finisher() {
        return s -> s;
      }

      @Override
      public java.util.Set<Characteristics> characteristics() {
        return EnumSet.of(Characteristics.IDENTITY_FINISH, Characteristics.UNORDERED);
      }
    };
  }

  public static <V> Collector<V, Set<V>, Set<V>> collector() {
    return new Collector<V, Set<V>, Set<V>>() {
      @Override
      public Supplier<Set<V>> supplier() {
        return () -> new Set<V>().linear();
      }

      @Override
      public BiConsumer<Set<V>, V> accumulator() {
        return Set::add;
      }

      @Override
      public BinaryOperator<Set<V>> combiner() {
        return Set::union;
      }

      @Override
      public Function<Set<V>, Set<V>> finisher() {
        return Set::forked;
      }

      @Override
      public java.util.Set<Characteristics> characteristics() {
        return EnumSet.of(Characteristics.UNORDERED);
      }
    };
  }
}
