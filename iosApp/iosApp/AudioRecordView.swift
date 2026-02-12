import SwiftUI
import AVFoundation
import Shared

struct AudioRecordView: View {
    @State private var isRecording = false
    @State private var recorder: (any AudioRecorder)?
    @State private var totalBytes = 0
    @State private var pcmChunks: [Data] = []
    @State private var audioPlayer: AVAudioPlayer?
    @State private var isPlaying = false
    @State private var hasRecording = false

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Text(isRecording ? "Kaydediliyor..." : "Basılı Tutarak Kaydet")
                .font(.title2)
                .foregroundColor(isRecording ? .red : .primary)

            Image(systemName: isRecording ? "mic.fill" : "mic.circle")
                .resizable()
                .scaledToFit()
                .frame(width: 100, height: 100)
                .foregroundColor(isRecording ? .red : .blue)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { _ in if !isRecording { startRecording() } }
                        .onEnded { _ in if isRecording { stopRecording() } }
                )

            if totalBytes > 0 {
                Text("Total PCM bytes: \(totalBytes)")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            if hasRecording {
                Button(action: togglePlayback) {
                    HStack(spacing: 8) {
                        Image(systemName: isPlaying ? "stop.fill" : "play.fill")
                        Text(isPlaying ? "Durdur" : "Kaydı Dinle")
                    }
                    .font(.headline)
                    .foregroundColor(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(isPlaying ? Color.red : Color.green)
                    .cornerRadius(25)
                }
                .padding(.top, 8)
            }

            Spacer()
        }
        .onDisappear {
            if isRecording { stopRecording() }
            stopPlayback()
        }
    }

    private func startRecording() {
        AVAudioSession.sharedInstance().requestRecordPermission { granted in
            guard granted else { return }
            DispatchQueue.main.async {
                stopPlayback()
                let rec = IOSAudioRecorderKt.createAudioRecorder()
                recorder = rec
                totalBytes = 0
                pcmChunks = []
                hasRecording = false
                rec.startRecording { data in
                    let size = Int(data.size)
                    let bytes = [UInt8](unsafeUninitializedCapacity: size) { buffer, count in
                        for i in 0..<size {
                            buffer[i] = UInt8(bitPattern: data.get(index: Int32(i)))
                        }
                        count = size
                    }
                    let chunk = Data(bytes)
                    DispatchQueue.main.async {
                        totalBytes += size
                        pcmChunks.append(chunk)
                    }
                }
                isRecording = true
            }
        }
    }

    private func stopRecording() {
        recorder?.stopRecording()
        recorder = nil
        isRecording = false
        if totalBytes > 0 {
            hasRecording = true
        }
    }

    private func togglePlayback() {
        if isPlaying {
            stopPlayback()
        } else {
            playRecording()
        }
    }

    private func playRecording() {
        guard !pcmChunks.isEmpty else { return }

        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback)
        try? session.setActive(true)

        var rawPCM = Data()
        for chunk in pcmChunks {
            rawPCM.append(chunk)
        }

        // Device mic default: 32-bit float, mono, 48kHz (or 44.1kHz)
        let sampleRate: Double = 48000
        let channels: UInt16 = 1
        let bitsPerSample: UInt16 = 32

        guard let wavData = createWAV(from: rawPCM, sampleRate: sampleRate, channels: channels, bitsPerSample: bitsPerSample) else { return }

        do {
            let player = try AVAudioPlayer(data: wavData)
            player.delegate = PlaybackDelegate.shared
            PlaybackDelegate.shared.onFinish = {
                DispatchQueue.main.async { isPlaying = false }
            }
            player.play()
            audioPlayer = player
            isPlaying = true
        } catch {
            print("Playback error: \(error)")
        }
    }

    private func stopPlayback() {
        audioPlayer?.stop()
        audioPlayer = nil
        isPlaying = false
    }

    private func createWAV(from pcmData: Data, sampleRate: Double, channels: UInt16, bitsPerSample: UInt16) -> Data? {
        let dataSize = UInt32(pcmData.count)
        let byteRate = UInt32(sampleRate) * UInt32(channels) * UInt32(bitsPerSample / 8)
        let blockAlign = channels * (bitsPerSample / 8)

        // IEEE float format tag
        let audioFormat: UInt16 = 3

        var header = Data()
        // RIFF header
        header.append(contentsOf: [UInt8]("RIFF".utf8))
        header.append(uint32: 36 + dataSize)
        header.append(contentsOf: [UInt8]("WAVE".utf8))
        // fmt chunk
        header.append(contentsOf: [UInt8]("fmt ".utf8))
        header.append(uint32: 16)
        header.append(uint16: audioFormat)
        header.append(uint16: channels)
        header.append(uint32: UInt32(sampleRate))
        header.append(uint32: byteRate)
        header.append(uint16: blockAlign)
        header.append(uint16: bitsPerSample)
        // data chunk
        header.append(contentsOf: [UInt8]("data".utf8))
        header.append(uint32: dataSize)

        header.append(pcmData)
        return header
    }
}

private class PlaybackDelegate: NSObject, AVAudioPlayerDelegate {
    static let shared = PlaybackDelegate()
    var onFinish: (() -> Void)?

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        onFinish?()
    }
}

private extension Data {
    mutating func append(uint32: UInt32) {
        var value = uint32.littleEndian
        append(Data(bytes: &value, count: 4))
    }
    mutating func append(uint16: UInt16) {
        var value = uint16.littleEndian
        append(Data(bytes: &value, count: 2))
    }
}
