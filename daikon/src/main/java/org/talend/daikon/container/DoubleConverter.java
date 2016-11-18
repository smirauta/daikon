package org.talend.daikon.container;

public class DoubleConverter extends Converter<Double> {

    @Override
    public Double convert(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else {
            return Double.parseDouble(value.toString());
        }
    }
}