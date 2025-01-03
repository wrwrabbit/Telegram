package org.telegram.messenger.fakepasscode;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class FilteredArrayList <E> extends ArrayList<E> {
    List<E> originalList;

    public FilteredArrayList(List<E> filteredList, List<E> originalList) {
        super(filteredList);
        this.originalList = originalList;
    }

    @Override
    public boolean add(E e) {
        originalList.add(e);
        return super.add(e);
    }

    @Override
    public E remove(int index) {
        E e = get(index);
        originalList.remove(e);
        return super.remove(index);
    }

    @Override
    public boolean remove(@Nullable Object o) {
        originalList.remove(o);
        return super.remove(o);
    }

    @Override
    public void clear() {
        originalList.clear();
        super.clear();
    }

    @Override
    public boolean addAll(@NonNull Collection<? extends E> c) {
        originalList.addAll(c);
        return super.addAll(c);
    }

    @Override
    public boolean addAll(int index, @NonNull Collection<? extends E> c) {
        E e = get(index);
        int originalIndex = originalList.indexOf(e);
        if (originalIndex != -1) {
            originalList.addAll(originalIndex, c);
        }
        return super.addAll(index, c);
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        if (fromIndex >= 0 && toIndex < size()) {
            E e1 = get(fromIndex);
            E e2 = get(toIndex);
            int originalFromIndex = originalList.indexOf(e1);
            int originalToIndex = originalList.indexOf(e2);
            if (originalFromIndex != -1 && originalToIndex != -1) {
                ListIterator<E> it = originalList.listIterator(fromIndex);
                for (int i=0, n=toIndex-fromIndex; i<n; i++) {
                    it.next();
                    it.remove();
                }
            }
        }
        super.removeRange(fromIndex, toIndex);
    }

    @Override
    public boolean removeAll(@NonNull Collection<?> c) {
        originalList.removeAll(c);
        return super.removeAll(c);
    }

    @Override
    public boolean retainAll(@NonNull Collection<?> c) {
        originalList.retainAll(c);
        return super.retainAll(c);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public boolean removeIf(@NonNull Predicate<? super E> filter) {
        originalList.removeIf(filter);
        return super.removeIf(filter);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void replaceAll(@NonNull UnaryOperator<E> operator) {
        originalList.replaceAll(operator);
        super.replaceAll(operator);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void sort(@Nullable Comparator<? super E> c) {
        originalList.sort(c);
        super.sort(c);
    }

    @Override
    public E set(int index, E element) {
        E e = get(index);
        int previousSameElementCount = 0;
        for (int i = 0; i < index; i++) {
            if (get(i) == e) {
                previousSameElementCount++;
            }
        }
        int originalIndex = -1;
        for (int i = 0; i < originalList.size(); i++) {
            if (originalList.get(i) == e) {
                previousSameElementCount--;
                if (previousSameElementCount < 0) {
                    originalIndex = i;
                    break;
                }
            }
        }
        originalList.set(originalIndex, element);
        return super.set(index, element);
    }
}
