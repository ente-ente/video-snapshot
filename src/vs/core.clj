(ns vs.core
  (:import [uk.co.caprica.vlcj.binding LibVlc]
           [uk.co.caprica.vlcj.discovery NativeDiscovery]
           [uk.co.caprica.vlcj.player MediaPlayer MediaPlayerFactory MediaPlayerEventAdapter]
           [java.io File])
  (:require [clojure.tools.logging :as log]))

(def VLC_ARGS
  (into-array String
              ["--intf" "dummy"
               "--vout" "dummy"
               "--no-audio"
               "--no-osd"
               "--no-spu"
               "--no-stats"
               "--no-sub-autodetect-file"
               "--no-inhibit"
               "--no-disable-screensaver"
               "--no-snapshot-preview"
               ]))

(defonce initialized (-> (NativeDiscovery.)
                         (.discover)))

(defn- mime-type-to-extension [mime-type]
  (if (= mime-type "image/jpeg")
    "jpg"
    (.substring mime-type (+ (.indexOf mime-type "/") 1))))

(defn- resolve-res [state data]
  (let [{:keys [player factory result]} state]
    (if (not (realized? result))
      (do
        (.stop player)
        (.release player)
        (.release factory)
        (deliver result data)))))

(defn- resolve-error [state error]
  (resolve-res state {:error error
                      :result nil}))

(defn- resolve-success [state result]
  (resolve-res state {:error nil
                      :result result}))

(defn- event-adapter [state]
  (let [{:keys [result position mime-type factory]} state]
    (proxy [MediaPlayerEventAdapter] []
      (playing [player]
        (log/trace "seeking")
        (if (not (.isSeekable player))
          (resolve-error state "file not seekable")
          (.setPosition player position)))
      (error [player]
        (resolve-error state "unknown error"))
      (positionChanged [player new-position]
        (log/trace "position changed:" new-position)
        (if (>= new-position (* position 0.9))
          (let [file (File/createTempFile
                      "snapshot"
                      (str "." (mime-type-to-extension mime-type)))]
            (log/trace "position reached")
            (.pause player)
            (.saveSnapshot player file))))
      (snapshotTaken [player filename]
        (log/debug "snapshot taken:" filename)
        (resolve-success state filename)))))


(defn create-snapshot
  "Creates a snapshot from the given video file at the specified position (a percentage, e.g. 0.15 is 15%).

  Returns a promise which receives a result in the format of:
  {:error <potential error>
  :result <temporary filename for snapshot}"
  [video-file position mime-type]
  (let [factory (MediaPlayerFactory. VLC_ARGS)
        player (.newHeadlessMediaPlayer factory)
        result (promise)
        state {:factory factory
               :result result
               :player player
               :mime-type mime-type
               :position position}]
    (if (not (.exists (File. video-file)))
      (resolve-error state "file does not exist")
      (do
        (.addMediaPlayerEventListener player (event-adapter state))
        (if (not (.startMedia player video-file (make-array String 0)))
          (resolve-error state "failed to start playing"))
        result))))
