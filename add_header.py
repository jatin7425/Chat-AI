import sys

def add_wav_header(pcm_file, wav_file, sample_rate=44100, channels=1, bit_depth=16):
    with open(pcm_file, 'rb') as f:
        pcm_data = f.read()

    total_audio_len = len(pcm_data)
    total_data_len = total_audio_len + 36
    byte_rate = sample_rate * channels * (bit_depth // 8)

    header = bytearray(44)
    header[0:4] = b'RIFF'
    header[4:8] = total_data_len.to_bytes(4, byteorder='little')
    header[8:12] = b'WAVE'
    header[12:16] = b'fmt '
    header[16:20] = int(16).to_bytes(4, byteorder='little')
    header[20:22] = int(1).to_bytes(2, byteorder='little') # format = 1
    header[22:24] = channels.to_bytes(2, byteorder='little')
    header[24:28] = sample_rate.to_bytes(4, byteorder='little')
    header[28:32] = byte_rate.to_bytes(4, byteorder='little')
    header[32:34] = int(channels * (bit_depth // 8)).to_bytes(2, byteorder='little')
    header[34:36] = bit_depth.to_bytes(2, byteorder='little')
    header[36:40] = b'data'
    header[40:44] = total_audio_len.to_bytes(4, byteorder='little')

    with open(wav_file, 'wb') as f:
        f.write(header)
        f.write(pcm_data)

add_wav_header('test6.wav', 'test6_header.wav')
