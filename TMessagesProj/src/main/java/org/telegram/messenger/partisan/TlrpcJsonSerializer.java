package org.telegram.messenger.partisan;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import org.telegram.messenger.Utilities;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;

import java.io.IOException;

public class TlrpcJsonSerializer extends StdSerializer<TLObject> {

    public TlrpcJsonSerializer() {
        this(null);
    }

    public TlrpcJsonSerializer(Class<TLObject> t) {
        super(t);
    }

    @Override
    public void serialize(TLObject value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        SerializedData serializedData = new SerializedData();
        value.serializeToStream(serializedData);
        jgen.writeString(Utilities.bytesToHex(serializedData.toByteArray()));
    }

    @Override
    public void serializeWithType(TLObject value, JsonGenerator jgen, SerializerProvider provider, TypeSerializer typeSer) throws IOException {
        typeSer.writeTypePrefixForScalar(value, jgen, value.getClass());
        serialize(value, jgen, provider);
        typeSer.writeTypeSuffixForScalar(value, jgen);
    }
}
