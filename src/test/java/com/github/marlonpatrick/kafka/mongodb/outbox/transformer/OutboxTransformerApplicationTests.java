package com.github.marlonpatrick.kafka.mongodb.outbox.transformer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import lombok.Setter;

@DirtiesContext
@ActiveProfiles("test")
@SpringBootTest(classes = OutboxTransformerApplication.class)
@EmbeddedKafka(topics = { "test.outbox.transformer.input.topic1", "test.outbox.transformer.input.topic2",
		"test.outbox.transformer.input.topic3", "test.outbox.transformer.input.topicMultiMessage", "test.outbox.transformer.output.topic1",
		"test.outbox.transformer.output.topic2", "test.outbox.transformer.output.topic3", 
		"test.outbox.transformer.output.topic4", "test.outbox.transformer.output.topic5", 
		"test.outbox.transformer.output.topic6"})
class OutboxTransformerApplicationTests {

	@Autowired
	private EmbeddedKafkaBroker embeddedKafka;

	private KafkaTemplate<String, String> template;

	@PostConstruct
	private void buildKafkaTemplate() {
		Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafka);

		producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

		producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

		ProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(producerProps);

		this.template = new KafkaTemplate<>(pf);
	}

	@Test
	public void when_outbox_has_more_then_one_message_output_more_than_one_message() {

		template.send("test.outbox.transformer.input.topicMultiMessage", "message-key-multi-message", readFile("when_outbox_has_more_then_one_message_output_more_than_one_message.json"));

		Consumer<String, TestOutboxMessage> consumer = consumeOutputTopics("test-outbox-transformer-output-topic-multi-message", 
			"test.outbox.transformer.output.topic4",
			"test.outbox.transformer.output.topic5", "test.outbox.transformer.output.topic6");

		ConsumerRecords<String, TestOutboxMessage> replies = KafkaTestUtils.getRecords(consumer, 10000, 3);

		assertThat(replies.count(), is(equalTo(3)));

		assertThat(replies.records("test.outbox.transformer.output.topic4"), is(not(emptyIterable())));

		assertThat(replies.records("test.outbox.transformer.output.topic5"), is(not(emptyIterable())));

		assertThat(replies.records("test.outbox.transformer.output.topic6"), is(not(emptyIterable())));

		List<String> possibleOutboxIds = List.of("message01", "message02", "message03");

		replies.forEach(cr -> {
			assertOutputMessagesRules(cr, "message-key-multi-message", "documentTestMultiMessage", possibleOutboxIds);
		});
	}


	@Test
	public void when_operation_type_is_insert_then_process_it() {

		template.send("test.outbox.transformer.input.topic1", "message-key-insert", readFile("when_operation_type_is_insert_then_process_it.json"));

		Consumer<String, TestOutboxMessage> consumer = consumeOutputTopics("test-outbox-transformer-output-topic-1",
				"test.outbox.transformer.output.topic1");

		ConsumerRecords<String, TestOutboxMessage> replies = KafkaTestUtils.getRecords(consumer, 10000, 1);

		assertThat(replies.count(), is(equalTo(1)));

		replies.forEach(cr -> {
			assertOutputMessagesRules(cr, "message-key-insert", "documentTestInsert", List.of("outboxTestInsert"));
		});
	}

	@Test
	public void when_operation_type_is_replace_then_process_it() {

		template.send("test.outbox.transformer.input.topic2", "message-key-replace", readFile("when_operation_type_is_replace_then_process_it.json"));

		Consumer<String, TestOutboxMessage> consumer = consumeOutputTopics("test-outbox-transformer-output-topic-2",
				"test.outbox.transformer.output.topic2");

		ConsumerRecords<String, TestOutboxMessage> replies = KafkaTestUtils.getRecords(consumer, 10000, 1);

		assertThat(replies.count(), is(equalTo(1)));

		replies.forEach(cr -> {
			assertOutputMessagesRules(cr, "message-key-replace", "documentTestReplace", List.of("outboxTestReplace"));
		});
	}

	@Test
	public void when_operation_type_is_update_then_process_it() {

		template.send("test.outbox.transformer.input.topic3", "message-key-update", readFile("when_operation_type_is_update_then_process_it.json"));

		Consumer<String, TestOutboxMessage> consumer = consumeOutputTopics("test-outbox-transformer-output-topic-3",
				"test.outbox.transformer.output.topic3");

		ConsumerRecords<String, TestOutboxMessage> replies = KafkaTestUtils.getRecords(consumer, 10000, 1);

		assertThat(replies.count(), is(equalTo(1)));

		replies.forEach(cr -> {
			assertOutputMessagesRules(cr, "message-key-update", "documentTestUpdate", List.of("outboxTestUpdate"));
		});
	}

	private String readFile(String fileName) {
		try {
			return Files.readString(Paths.get("src/test/resources/" + fileName));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Consumer<String, TestOutboxMessage> consumeOutputTopics(String group, String... topics) {

		Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(group, "true", embeddedKafka);

		consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

		consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

		DefaultKafkaConsumerFactory<String, TestOutboxMessage> cf = new DefaultKafkaConsumerFactory<>(consumerProps);

		cf.setValueDeserializer(new JsonDeserializer<>(TestOutboxMessage.class));

		Consumer<String, TestOutboxMessage> consumer = cf.createConsumer();

		this.embeddedKafka.consumeFromEmbeddedTopics(consumer, topics);

		return consumer;
	}
	
	private void assertOutputMessagesRules(ConsumerRecord<String, TestOutboxMessage> cr, String key, String entityId, List<String> possibleOutboxIds){

		assertThat(cr.headers().headers("__TypeId__"), is(emptyIterable()));

		assertThat(cr.key(), is(equalTo(key)));

		TestOutboxMessage outboxMessage = cr.value();

		assertThat(outboxMessage, is(notNullValue()));

		assertThat(outboxMessage._id, is(nullValue()));

		assertThat(outboxMessage.targetTopic, is(nullValue()));

		assertThat(outboxMessage.id, is(in(possibleOutboxIds)));

		assertThat(outboxMessage.entityId, is(equalTo(entityId)));

		assertThat(outboxMessage.payload.keySet(), is(not(empty())));
	}
}

@Setter
class TestOutboxMessage {

	String id;

	String _id;

	String createdAt;

	String entityName;

	String entityId;

	String messageName;

	String targetTopic;

	Map<String, Object> payload;
}
