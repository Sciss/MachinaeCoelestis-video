    avconv -i image_out2/frame_rsmp-%d.png -c:v libx264 -r 25 -vf "transpose=2" -f mp4 video_out/out2.mp4

