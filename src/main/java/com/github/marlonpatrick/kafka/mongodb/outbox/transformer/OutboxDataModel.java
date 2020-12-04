package com.github.marlonpatrick.kafka.mongodb.outbox.transformer;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 *<p>
 * Enum type representing the MongoDB operations types which can result in messages to this Kafka Streams application.
 *</p>
 */
enum OperationType {
	INSERT, UPDATE, REPLACE,
}

/**
 *<p>
 * This class represent a complete INPUT message to be processed in this Kafka Streams application.
 *</p>
 */
@ToString
@Setter
class RawOutboxDocument {
	private String operationType;
	OutboxHolder fullDocument;
	UpdateDescription updateDescription;

	OperationType operationType() {
		try {
			return OperationType.valueOf(this.operationType.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new UnsupportedOperationException(String.format("The operationType %s is not supported.", this.operationType));
		}
	}
}

/**
 *<p>
 * This class represent a json object which has an array field called "outbox".
 * The "outbox" field is the one who has all the outbox messages to be sent to the
 * target topics.
 *</p>
 */
@ToString
@Setter
class OutboxHolder {
	List<OutboxMessage> outbox;
}

/**
 *<p>
 * This class represents a json object that details the modifications in the MongoDB document in case the operation type is UPDATE.
 *</p>
 */
@ToString
@Setter
class UpdateDescription {
	OutboxHolder updatedFields;
}

/**
 *<p>
 * This class represents a single outbox message which should be sent to its intended target topic.
 *</p> 
 *<p>
 * There are 2 points of attention in the serialization / deserialization of this class:
 *</p> 
 *<p>
 * 1 - In the input message, there is a field called "_id", so when deserializing (setId) the message we need
 * tell Jackson to uses the field named "_id". However, in the output message sent to the target topics we change 
 * the name of the field to simply "id", that way, when serializing (getId) the message we need to tell Jackson 
 * to name the field "id". 
 *<p>
 * 2 - In the input message, there is a field called targetTopic, which defines exactly which topic this message should be sent to. 
 * However, when sending the message to the target topic, this field no longer makes sense and so we removed it. 
 * Thus, we define a setter for the targetTopic field, so that when deserializing the input message Jackson loads the field, 
 * however, we DO NOT define a getter so that Jackson does not serialize this field when sending the message to the target topic . 
 *</p>
 */
@ToString
class OutboxMessage {

	String id;

	@JsonProperty("id")
	public String getId() {
		return id;
	}

	@JsonProperty("_id")
	public void setId(String id) {
		this.id = id;
	}

	@Getter
	@Setter
	String createdAt;

	@Getter
	@Setter
	String entityName;

	@Getter
	@Setter
	String entityId;

	@Getter
	@Setter
	String messageName;

	@Setter
	String targetTopic;

	@Getter
	@Setter
	Map<String, Object> payload;

	// TODO: extra properties
}
