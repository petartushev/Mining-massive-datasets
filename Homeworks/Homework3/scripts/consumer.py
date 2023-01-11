import pandas as pd
import kafka

consumer = kafka.KafkaConsumer(bootstrap_servers='localhost:9092', security_protocol='PLAINTEXT')
consumer.subscribe(topics=['health_data_predicted'])

while True:
    
    msg = consumer.poll(timeout_ms=1000, max_records=1000)

    print(msg.values())
