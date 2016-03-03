// ============================================================================
//
// Copyright (C) 2006-2016 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.daikon.talend6;

import java.util.HashMap;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.generic.IndexedRecord;
import org.talend.daikon.avro.IndexedRecordAdapterFactory.UnmodifiableAdapterException;

/**
 * This class acts as a wrapper around an arbitrary Avro {@link IndexedRecord} to coerce the output type to the exact
 * Java objects expected by the Talend 6 Studio (which will copy the fields into a POJO in generated code).
 * 
 * A wrapper like this should be attached to an input component, for example, to ensure that its outgoing data meets the
 * Schema constraints imposed by the Studio.
 * 
 * One instance of this object can be created per outgoing schema and reused via the {@link #setWrapped(IndexedRecord)}
 * method.
 */
public class Talend6SchemaOutputEnforcer implements IndexedRecord, Talend6SchemaConstants {

    /** True if columns from the incoming schema are matched to the outgoing schema exclusively by position. */
    final private boolean byIndex;

    /** The outgoing schema that determines which Java objects are produced. */
    final private Schema outgoing;

    /**
     * The incoming IndexedRecord currently wrapped by this enforcer. This can be swapped out for new data as long as
     * they keep the same schema.
     */
    private IndexedRecord wrapped;

    /**
     * The position of the dynamic column in the outgoing schema. This is -1 if there is no dynamic column. There can be
     * a maximum of one dynamic column in the schema.
     */
    private final int dynamicColumn;

    public static final String TALEND6_DYNAMIC_TYPE = "DYNAMIC"; //$NON-NLS-1$

    public Talend6SchemaOutputEnforcer(Schema outgoing, boolean byIndex) {
        this.outgoing = outgoing;
        this.byIndex = byIndex;

        // Find the dynamic column, if any.
        int dynamic = -1;
        for (Field f : outgoing.getFields()) {
            if (TALEND6_DYNAMIC_TYPE.equals(f.getProp(TALEND6_COLUMN_TALEND_TYPE))) {
                if (dynamic != -1) {
                    // This is enforced by the Studio.
                    throw new UnsupportedOperationException("Too many dynamic columns."); //$NON-NLS-1$
                }
                dynamic = f.pos();
            }
        }
        dynamicColumn = dynamic;
    }

    /**
     * @param wrapped Sets the internal, actual index record that needs to be coerced to the outgoing schema.
     */
    public void setWrapped(IndexedRecord wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public Schema getSchema() {
        return outgoing;
    }

    @Override
    public void put(int i, Object v) {
        throw new UnmodifiableAdapterException();
    }

    @Override
    public Object get(int i) {

        Field outField = getSchema().getFields().get(i);
        int wrappedIndex = i;
        Field wrappedField = null;

        // Find the incoming field the corresponds to the outgoing field.
        if (byIndex) {
            if (dynamicColumn != -1) {

                // The number of columns that we will place in the dynamic field.
                int dynColN = wrapped.getSchema().getFields().size() - getSchema().getFields().size() + 1;
                if (dynColN < 0) {
                    throw new UnsupportedOperationException(
                            "The incoming data does not have sufficient columns to create a dynamic column."); //$NON-NLS-1$
                }

                if (dynamicColumn == i) {
                    // If we're trying to fetch the dynamic column, we're actually wanting a Map<String, Object> as a
                    // value containing all the non-constrained columns.
                    Map<String, Object> result = new HashMap<>();
                    for (int j = 0; j < dynColN; j++) {
                        result.put(wrapped.getSchema().getFields().get(dynamicColumn + j).name(), wrapped.get(dynamicColumn + j));
                    }
                    return result;

                } else if (dynamicColumn != -1 && dynamicColumn == -1) {
                    // If we're looking for a value after the dynamic column, match the field at the end of the incoming
                    // record.
                    wrappedIndex = dynColN - 1 + i;
                }
            }

            // Get the field from the wrapped schema, if it exists.
            if (wrappedIndex < wrapped.getSchema().getFields().size()) {
                wrappedField = wrapped.getSchema().getFields().get(wrappedIndex);
            }

        } else {
            // TODO(rskraba): Can we resolve the field by name if there's a dynamic column? Do we just need to count all
            // of the fields that do not match by name?
            wrappedField = wrapped.getSchema().getField(outField.name());
            if (wrappedField != null) {
                wrappedIndex = wrappedField.pos();
            }
        }

        // If the wrapped field is null, then return the default value from the outgoing schema.
        Object value = wrappedField == null ? outField.getProp(TALEND6_COLUMN_DEFAULT) : wrapped.get(wrappedIndex);
        return transformValue(value, wrappedField, outField);
    }

    /**
     * @param value
     * @param wrappedField
     * @param outField
     * @return
     */
    private Object transformValue(Object value, Field wrappedField, Field outField) {
        if (null == outField.getProp(TALEND6_COLUMN_TALEND_TYPE)) {
            return value;
        }
        // TODO: to implement.
        return value;
    }

    /**
     * @Return true if the Avro Field has been tagged with a type, and the type is DYNAMIC.
     */
    public static boolean isDynamic(Field f) {
        return TALEND6_DYNAMIC_TYPE.equals(f.getProp(TALEND6_COLUMN_TALEND_TYPE));
    }

    /**
     * @Return true if the Avro Schema has a field of type DYNAMIC
     */
    public static boolean isDynamic(Schema f) {
        // A dynamic
        return null != f.getProp(TALEND6_TABLE_DYNAMIC_COLUMN);
    }
}