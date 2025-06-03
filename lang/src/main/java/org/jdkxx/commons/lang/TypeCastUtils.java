package org.jdkxx.commons.lang;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class TypeCastUtils {
    private static final String FORMAT_T = "yyyy-MM-dd'T'HH:mm:ss";

    public static Object cast(String value, Type type) throws Exception {
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            if (ClassUtils.isAssignable((Class<?>) pt.getRawType(), List.class)) {
                List<Object> list = new ArrayList<>();
                Type[] types = pt.getActualTypeArguments();
                if (types.length > 0) {
                    String[] values = StringUtils.split(value, ",");
                    for (String v : values) {
                        list.add(cast(v, types[0]));
                    }
                }
                return list;
            } else if (ClassUtils.isAssignable((Class<?>) pt.getRawType(), Map.class)) {
                // Using Map<Object, Object> for more flexibility
                Map<Object, Object> map = new HashMap<>();
                Type[] types = pt.getActualTypeArguments();
                if (types.length > 1) {
                    String[] pairs = StringUtils.split(value, ",");
                    for (String pair : pairs) {
                        int idx = pair.indexOf(":"); // Find the ':' character
                        Object k = cast(pair.substring(0, idx), types[0]);
                        Object v = cast(pair.substring(idx + 1), types[1]);
                        map.put(k, v);
                    }
                }
                return map;
            } else if (ClassUtils.isAssignable((Class<?>) pt.getRawType(), Set.class)) {
                Set<Object> set = new HashSet<>();
                Type[] types = pt.getActualTypeArguments();
                String[] values = StringUtils.split(value, ",");
                for (String v : values) {
                    set.add(cast(v, types[0]));
                }
                return set;
            }
        } else if (((Class<?>) type).isArray()) {
            String[] values = StringUtils.split(value, ",");
            return castToArray(values, type);
        } else if (ClassUtils.isAssignable((Class<?>) type, Byte.TYPE)) {
            return Byte.parseByte(value);
        } else if (ClassUtils.isAssignable((Class<?>) type, Short.TYPE)) {
            return Short.parseShort(value);
        } else if (ClassUtils.isAssignable((Class<?>) type, Integer.TYPE)) {
            return Integer.parseInt(value);
        } else if (ClassUtils.isAssignable((Class<?>) type, Long.TYPE)) {
            return Long.parseLong(value);
        } else if (ClassUtils.isAssignable((Class<?>) type, Float.TYPE)) {
            return Float.parseFloat(value);
        } else if (ClassUtils.isAssignable((Class<?>) type, Double.TYPE)) {
            return Double.parseDouble(value);
        } else if (ClassUtils.isAssignable((Class<?>) type, String.class)) {
            return value;
        } else if (ClassUtils.isAssignable((Class<?>) type, Boolean.TYPE)) {
            return Boolean.parseBoolean(value);
        } else if (ClassUtils.isAssignable((Class<?>) type, Character.TYPE)) {
            return value.charAt(0);
        } else if (ClassUtils.isAssignable((Class<?>) type, BigDecimal.class)) {
            return new BigDecimal(value);
        } else if (ClassUtils.isAssignable((Class<?>) type, BigInteger.class)) {
            return new BigInteger(value);
        } else if (ClassUtils.isAssignable((Class<?>) type, Date.class)) {
            DateFormat df = new SimpleDateFormat(FORMAT_T);
            return df.parse(value);
        }
        throw new ClassCastException("Cannot cast " + value + " to " + type);
    }

    private static Object castToArray(String[] values, Type type) throws Exception {
        Class<?> cls = ((Class<?>) type).getComponentType();
        if (ClassUtils.isAssignable(cls, Short.TYPE)) {
            Short[] array = new Short[values.length];
            for (int i = 0; i < values.length; i++) {
                array[i] = (Short) cast(values[i], cls);
            }
            return array;
        } else if (ClassUtils.isAssignable(cls, Integer.TYPE)) {
            Integer[] array = new Integer[values.length];
            for (int i = 0; i < values.length; i++) {
                array[i] = (Integer) cast(values[i], cls);
            }
            return array;
        } else if (ClassUtils.isAssignable(cls, Long.TYPE)) {
            Long[] array = new Long[values.length];
            for (int i = 0; i < values.length; i++) {
                array[i] = (Long) cast(values[i], cls);
            }
            return array;
        } else if (ClassUtils.isAssignable(cls, Float.TYPE)) {
            Float[] array = new Float[values.length];
            for (int i = 0; i < values.length; i++) {
                array[i] = (Float) cast(values[i], cls);
            }
            return array;
        } else if (ClassUtils.isAssignable(cls, Double.TYPE)) {
            Double[] array = new Double[values.length];
            for (int i = 0; i < values.length; i++) {
                array[i] = (Double) cast(values[i], cls);
            }
            return array;
        } else if (ClassUtils.isAssignable(cls, String.class)) {
            return values;
        } else if (ClassUtils.isAssignable(cls, Boolean.TYPE)) {
            Boolean[] array = new Boolean[values.length];
            for (int i = 0; i < values.length; i++) {
                array[i] = (Boolean) cast(values[i], cls);
            }
            return array;
        } else if (ClassUtils.isAssignable(cls, BigDecimal.class)) {
            BigDecimal[] array = new BigDecimal[values.length];
            for (int i = 0; i < values.length; i++) {
                array[i] = (BigDecimal) cast(values[i], cls);
            }
            return array;
        } else if (ClassUtils.isAssignable(cls, BigInteger.class)) {
            BigInteger[] array = new BigInteger[values.length];
            for (int i = 0; i < values.length; i++) {
                array[i] = (BigInteger) cast(values[i], cls);
            }
            return array;
        } else if (ClassUtils.isAssignable(cls, Date.class)) {
            Date[] array = new Date[values.length];
            for (int i = 0; i < values.length; i++) {
                array[i] = (Date) cast(values[i], cls);
            }
            return array;
        }
        throw new ClassCastException("Cannot cast " + Arrays.toString(values) + " to " + type);
    }
}
