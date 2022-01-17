import pandas as pd
import numpy as np
import time
import multiprocessing
import os
import paho.mqtt.client as paho
import ssl
import os.path
import random
import string


broker = "X.X.X.X"
listener = X
timeout = X
cert_file = "X"

def connect(client, userdata, flags, rc):
    print("Connected with result code " + str(rc))
    mqtt_client.subscribe("sensors/#", 2)

def message(clnt, userdata, msg):
    #print(msg.topic + " " + str(msg.payload))
    file_path = 'data/' + str(msg.topic).replace("/", "_")  + ".csv"
    values = str(msg.payload.decode("utf-8")).split(";")
    columns = []
    first_time = True
    if os.path.isfile(file_path):
        first_time = False
    if "mic" in file_path:
        columns = ['rms', 'spl']
    if "9oD" in file_path:
        columns = ['mx', 'my', 'mz','ax','ay','az','gx','gy','gz']
    if "contact" in file_path:
        columns = ['status']
    data_to_csv = pd.DataFrame([values[1:]], index = [values[0]], columns = columns)
    data_to_csv.to_csv(file_path, mode= 'a', header = first_time)

all_letters = string.ascii_letters
client_id = ''.join(random.choice(all_letters) for i in range(6)) #random client_id (upper and lowercase)

mqtt_client = paho.Client(client_id=client_id, clean_session=False)
mqtt_client.on_connect = connect
mqtt_client.on_message = message
mqtt_client.tls_set(cert_file, tls_version = ssl.PROTOCOL_TLSv1_2)
mqtt_client.tls_insecure_set(True)
mqtt_client.connect(broker, listener, timeout)
mqtt_client.loop_forever()
