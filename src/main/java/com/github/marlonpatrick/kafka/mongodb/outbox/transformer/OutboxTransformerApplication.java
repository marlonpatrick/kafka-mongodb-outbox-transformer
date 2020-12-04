package com.github.marlonpatrick.kafka.mongodb.outbox.transformer;

import java.util.regex.Pattern;

import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.Produced;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.support.serializer.JsonSerde;

@SpringBootApplication
@EnableKafkaStreams
public class OutboxTransformerApplication {

	public static void main(String[] args) {
		SpringApplication.run(OutboxTransformerApplication.class, args);
	}

	@Bean
	public KStream<String, RawOutboxDocument> kStream(StreamsBuilder kStreamBuilder, Environment env) {

		String inputTopicPattern = env.getProperty("outbox.transformer.kafka.consumer.topic.pattern");

		KStream<String, RawOutboxDocument> stream = kStreamBuilder.stream(Pattern.compile(inputTopicPattern),
				Consumed.with(null, new JsonSerde<>(RawOutboxDocument.class)));

		if (env.getProperty("outbox.transformer.kafka.streams.log.sysout", Boolean.class)) {
			stream.print(Printed.toSysOut());
		}

		stream.flatMapValues(completeDocument -> {

			if (completeDocument.operationType().equals(OperationType.UPDATE)) {
				return completeDocument.updateDescription.updatedFields.outbox;
				// TODO: handle case of outbox.0, outbox.1, etc
			}

			return completeDocument.fullDocument.outbox;
		}).to((k, v, recordContext) -> v.targetTopic, Produced.valueSerde(new JsonSerde<>(OutboxMessage.class).noTypeInfo()));

		return stream;
	}
}
