# video-snapshot

Library for taking snapshots from videos

Example:
```clojure
(use 'vs.core)

@(create-snapshot
   "/tmp/testvideo.mp4"
   0.20
   "image/png")

; {:error nil, :result /tmp/snapshot5938238847113782779.png}
```
