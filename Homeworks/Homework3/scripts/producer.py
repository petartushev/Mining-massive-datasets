from ensurepip import bootstrap
import imp
import json 
import kafka
import pandas as pd
import time
import random

producer = kafka.KafkaProducer(bootstrap_servers='localhost:9092', security_protocol='PLAINTEXT')

df = pd.read_csv('./data/smoker/online_stratified_20/part-00000-01f0ffb9-1f2b-4953-9232-2fd03fc5cbf9-c000.csv')

df.drop(['Smoker'], axis=1, inplace=True)

for row in df.iterrows():

    # print(row[1].to_json())

    producer.send(
        topic='health_data',
        value=row[1].to_json().encode('utf-8')
    )

    time.sleep(random.randint(100, 1500)/1000.0)