#!/usr/bin/python

import pandas as pd
import numpy as np
import time
import multiprocessing
import os
import paho.mqtt.client as paho
import ssl
import pyaudio
import wave
import time
import csv
from icm20948 import ICM20948
import audioop
import math
import RPi.GPIO as GPIO
import threading
import random
import string

pin = X

GPIO.setmode(GPIO.BCM)
GPIO.setup(pin, GPIO.IN, pull_up_down=GPIO.PUD_UP)

broker = "X.X.X.X"
listener = X
timeout = X
client_id = 'X'
cert_file = "X"

CHUNK = 400 #the number of samples in a chunk to record
FORMAT = pyaudio.paInt16 #2 bytes
CHANNELS = 1
RATE = 48000 #48000 samples per sec

imu = ICM20948()
p = pyaudio.PyAudio()

stream = p.open(format=FORMAT,
                channels=CHANNELS,
                rate=RATE,
                input=True,
                frames_per_buffer=CHUNK)


def start_sensors(sub_name, client_id, trial):
    global started
    global p
    global stream
    global mqtt_client
    path = "/home/pi/data/" + client_id + "_" + sub_name + "_" + str(trial) + "_" + time.strftime("%Y%m%d-%H%M")
    status = 'close'
    list_m = []
    list_a = []
    list_cont = []
    while True:
        x, y, z = imu.read_magnetometer_data()
        ax, ay, az, gx, gy, gz = imu.read_accelerometer_gyro_data()
        values = tuple((time.time(),x,y,z,ax,ay,az,gx,gy,gz))
        mess = ";".join(str(x) for x in values)
        list_a.append(values)
        mqtt_client.publish('sensors/' + client_id + "/9oD/" + sub_name + "/trial/" + trial, mess , 2, retain=False, properties=None)
        if GPIO.input(pin):
            if status == 'close':
                values = tuple((time.time(), "open"))
                list_cont.append(values)
                mess = ";".join(str(x) for x in values)
                mqtt_client.publish('sensors/' + client_id + "/contact/" + sub_name + "/trial/" + trial, mess , 2, retain=False, properties=None)
            status='open'
        if GPIO.input(pin) == False:
            if status == 'open':
                values = tuple((time.time(), "close"))
                list_cont.append(values)
                mess = ";".join(str(x) for x in values)
                mqtt_client.publish('sensors/' + client_id + "/contact/" + sub_name + "/trial/" + trial, mess , 2, retain=False, properties=None)
            status ='close'
        data = stream.read(CHUNK, exception_on_overflow=False)
        rms_audio = audioop.rms(data, 2) #calc rms amplitude, width=2 corresponds to 2 bytes of FORMAT
        spl = 20 * math.log10(rms_audio) #in db(SPL)
        values = tuple((time.time(), rms_audio, spl))
        list_m.append(values)
        mess = ";".join(str(x) for x in values)
        mqtt_client.publish('sensors/' + client_id + "/mic/" + sub_name + "/trial/" + trial, mess, 2, retain=False, properties=None)
        if started:
            mic_data = pd.DataFrame(list_m, columns=['time_s', 'rms', 'spl'])
            mic_data.to_csv(path + "_mic" + '.csv')
            cont_data = pd.DataFrame(list_cont, columns=['time_s','status'])
            cont_data.to_csv(path + '_contact' + '.csv')
            accel_data = pd.DataFrame(list_a, columns=['time_s','mx', 'my', 'mz','ax','ay','az','gx','gy','gz'])
            accel_data.to_csv(path + '_acc' + '.csv')
        else:
            break

def connect(client, userdata, flags, rc):
    mqtt_client.subscribe("android/#", 2)
    mqtt_client.subscribe("init", 2)

proc = []
proc_count = 0
started = False
name = ""

def message(clnt, userdata, msg):
    global proc
    global proc_count
    global started
    global name
    message = str(msg.payload.decode("utf-8")).split(";")
    name = message[0]
    proc_count = message[-1]
    #print(msg.topic + " " + msg.payload.decode("utf-8"))
    if msg.topic == "init":
        mqtt_client.publish('active/' + client_id , "True", 2, retain=False, properties=None)
    if msg.topic == "android/start":
       started = True
       thread = threading.Thread(target = start_sensors, args=(name, client_id, proc_count))
       thread.start()
    if msg.topic == "android/stop":
       if started:
          started = False

all_letters = string.ascii_letters
id = ''.join(random.choice(all_letters) for i in range(6)) #random client_id (upper and lowercase)

mqtt_client = paho.Client(client_id=client_id, clean_session=False)
mqtt_client.on_connect = connect
mqtt_client.on_message = message
mqtt_client.tls_set(cert_file, tls_version = ssl.PROTOCOL_TLSv1_2)
mqtt_client.tls_insecure_set(True)
mqtt_client.connect(broker, listener, timeout)
mqtt_client.publish('script/started' + client_id , "True", 2, retain=False, properties=None)
mqtt_client.loop_forever()
