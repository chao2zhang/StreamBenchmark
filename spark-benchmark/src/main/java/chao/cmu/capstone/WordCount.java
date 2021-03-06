package chao.cmu.capstone;

import kafka.serializer.StringDecoder;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaPairInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka.KafkaUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import scala.Tuple2;

import java.util.*;

public class WordCount {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: <brokers> <topic>");
            System.exit(1);
        }

        String brokers = args[0];
        String topic = args[1];

        SparkConf sparkConf = new SparkConf()
                .setAppName("spark-benchmark-wordcount")
                .set("spark.streaming.backpressure.enabled", "true")
                .set("spark.streaming.ui.retainedBatches", "300");
        JavaStreamingContext jssc = new JavaStreamingContext(sparkConf, Durations.seconds(1));

        HashSet<String> topicSet = new HashSet<>();
        topicSet.add(topic);

        HashMap<String, String> kafkaParams = new HashMap<>();
        kafkaParams.put("bootstrap.servers", brokers);
        kafkaParams.put("auto.offset.reset", "largest");

        JavaPairInputDStream<String, String> messages = KafkaUtils.createDirectStream(
                jssc, String.class, String.class, StringDecoder.class, StringDecoder.class, kafkaParams, topicSet);

        JavaDStream<String> lines = messages.map(new Function<Tuple2<String, String>, String>() {
            @Override
            public String call(Tuple2<String, String> tuple2) {
                JSONObject object = (JSONObject) JSONValue.parse(tuple2._2());
                String text = (String) object.get("text");
                return text;
            }
        });

        JavaDStream<String> words = lines.flatMap(new FlatMapFunction<String, String>() {
            @Override
            public Iterable<String> call(String s) throws Exception {
                return Arrays.asList(s.split("\\s+"));
            }
        });

        JavaPairDStream<String, Long> counts = words.countByValue();
        counts.print();
        jssc.start();
        jssc.awaitTermination();
    }
}
