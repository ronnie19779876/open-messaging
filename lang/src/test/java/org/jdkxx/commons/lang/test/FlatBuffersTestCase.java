package org.jdkxx.commons.lang.test;

import com.google.flatbuffers.FlatBufferBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jdkxx.commons.events.model.Event;
import org.jdkxx.commons.events.model.EventSource;
import org.jdkxx.commons.events.model.Type;
import org.junit.Test;
import sun.nio.ch.DirectBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

@Slf4j
//@RunWith(Parameterized.class)
public class FlatBuffersTestCase {
    private final static byte[] TYPES = new byte[]{Type.READ, Type.DDL, Type.DELETE, Type.UPDATE, Type.INSERT};

    private ByteBuffer[] prepared() {
        ByteBuffer[] buffers = new ByteBuffer[1000000];
        for (int i = 0; i < buffers.length; i++) {
            //FlatBufferBuilder builder = new FlatBufferBuilder(1024, FlatBufferBuilder.HeapByteBufferFactory.INSTANCE);
            FlatBufferBuilder builder = new FlatBufferBuilder(1024, DirectByteBufferFactory.INSTANCE);
            int sourceOffset = builder.createString("source" + (i + 1));

            EventSource.startEventSource(builder);
            EventSource.addSource(builder, sourceOffset);
            int eventSourceOffset = EventSource.endEventSource(builder);

            Event.startEvent(builder);
            Event.addType(builder, TYPES[i % TYPES.length]);
            Event.addSource(builder, eventSourceOffset);
            int offset = Event.endEvent(builder);
            builder.finish(offset);
            buffers[i] = builder.dataBuffer();
        }
        return buffers;
    }

    //@Parameterized.Parameters
    /*
    public static Object[][] parameters() {
        ByteBuffer[] buffers = prepared();
        Object[][] parameters = new Object[buffers.length][];
        for (int i = 0; i < buffers.length; i++) {
            parameters[i] = new Object[]{buffers[i]};
        }
        return parameters;
    }*/

    //private ByteBuffer buffer;

    //public FlatBuffersTestCase(ByteBuffer buffer) {
    //    this.buffer = buffer;
    //}

    @Test
    public void test_readByteBuffers() throws Exception {
        ByteBuffer[] buffers = prepared();
        for (ByteBuffer buffer : buffers) {
            //log.info("buffer size:{}, isDirect: {}", buffer.position(), buffer.isDirect());
            Event event = Event.getRootAsEvent(buffer);
            EventSource source = event.source();
            log.info("event source: {}, type: {}", source.source(), event.type());

            if (buffer.isDirect()) {
                ((DirectBuffer) buffer).cleaner().clean();
            } else {
                buffer.clear();
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
    }

    private static final class DirectByteBufferFactory extends FlatBufferBuilder.ByteBufferFactory {
        public static final DirectByteBufferFactory INSTANCE = new DirectByteBufferFactory();

        public ByteBuffer newByteBuffer(int capacity) {
            return ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN);
        }

        @Override
        public void releaseByteBuffer(ByteBuffer bb) {
            if (bb != null) {
                bb.clear();
            }
        }
    }
}
