package dev.javafornestjs.generics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenericLabTest {
    private List<String> declaredNames;

    @Test
    void pecsCopiesIntegersToNumberAndObjectConsumers() {
        List<Integer> source = List.of(1, 2);
        List<Number> numbers = new ArrayList<>();
        List<Object> objects = new ArrayList<>();
        GenericLab.copyValues(source, numbers);
        GenericLab.copyValues(source, objects);
        assertEquals(List.of(1, 2), numbers);
        assertEquals(List.of(1, 2), objects);
        assertEquals(4, GenericLab.maxNatural(2, 4));
    }

    @Test
    void erasedRuntimeClassDoesNotEraseAllDeclarationMetadata() throws Exception {
        assertEquals(new ArrayList<String>().getClass(), new ArrayList<Integer>().getClass());
        assertEquals(Object.class, GenericLab.class.getMethod("identity", Object.class).getReturnType());
        assertEquals(Number.class, GenericLab.class.getMethod("maxNatural", Number.class, Number.class)
                .getReturnType());
        var type = GenericLabTest.class.getDeclaredField("declaredNames").getGenericType();
        var parameterized = assertInstanceOf(ParameterizedType.class, type);
        assertEquals(String.class, parameterized.getActualTypeArguments()[0]);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void deliberateRawTypePollutionFailsAtTypedRead() {
        List<String> strings = new ArrayList<>();
        List raw = strings;
        raw.add(42);
        assertThrows(ClassCastException.class, () -> strings.get(0).length());
    }
}
