package com.github.marlonpatrick.kafka.mongodb.outbox.transformer;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.processor.WallclockTimestampExtractor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.support.serializer.JsonSerde;

@SpringBootApplication
@EnableKafkaStreams
public class OutboxTransformerApplication {

	public static void main(String[] args) {
		SpringApplication.run(OutboxTransformerApplication.class, args);
	}

	@Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
	public KafkaStreamsConfiguration kStreamsConfigs() {
		Map<String, Object> props = new HashMap<>();
		props.put(StreamsConfig.APPLICATION_ID_CONFIG, "kafka-mongodb-outbox-transformer-x");
		props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
		props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
		props.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, WallclockTimestampExtractor.class.getName());
		return new KafkaStreamsConfiguration(props);
	}

	@Bean
	public KStream<String, RawOutboxDocument> kStream(StreamsBuilder kStreamBuilder, Environment env) {
		KStream<String, RawOutboxDocument> stream = kStreamBuilder.stream(Pattern.compile(env.getProperty("outbox.transformer.kafka.consumer.topic.pattern")),
				Consumed.with(null, new JsonSerde<>(RawOutboxDocument.class)));

		stream.flatMapValues(completeDocument -> {

			if (completeDocument.operationType().equals(OperationType.UPDATE)) {
				return completeDocument.updateDescription.updatedFields.outbox;
				// TODO: handle case of outbox.0, outbox.1, etc
			}

			return completeDocument.fullDocument.outbox;
		})
		.to((k, v, recordContext) -> v.targetTopic, Produced.valueSerde(new JsonSerde<>(OutboxMessage.class)));

		return stream;
	}
}

