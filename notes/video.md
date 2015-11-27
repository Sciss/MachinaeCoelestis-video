    avconv -i image_out2/frame_rsmp-%d.png -c:v libx264 -r 25 -vf "transpose=2" -f mp4 video_out/out2.mp4

## Web Assemble

    avconv -i web_assemble/frame-%d.png -c:v libx264 -r 25 -vf "transpose=2,fade=type=in:start_frame=0:nb_frames=25,fade=type=out:start_frame=926:nb_frames=25,fade=type=in:start_frame=951:nb_frames=25,fade=type=out:start_frame=2177:nb_frames=25" -f mp4 video_out/web_assemble.mp4

    avconv -i web_assemble/frame-%d.png -c:v libx264 -r 25 -vf "transpose=2,fade=type=in:start_frame=0:nb_frames=100,fade=type=out:start_frame=851:nb_frames=100" -start_number 1 -vframes 951 -f mp4 video_out/web_1.mp4

