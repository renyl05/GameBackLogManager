package gbm;

import java.awt.image.BufferedImage;

/** Uses only alpha connectivity: pale surfaces inside the subject are never keyed out. */
final class MenuIconArt {
  static BufferedImage subject(BufferedImage source){
    int width=source.getWidth(),height=source.getHeight(),size=width*height;
    boolean[] seen=new boolean[size];int[] queue=new int[size],largest=new int[0];
    for(int start=0;start<size;start++){
      if(seen[start]||(source.getRGB(start%width,start/width)>>>24)<24)continue;
      int tail=1;queue[0]=start;seen[start]=true;
      for(int head=0;head<tail;head++){
        int pos=queue[head],x=pos%width,y=pos/width;
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
          int nx=x+dx,ny=y+dy;if(nx<0||nx>=width||ny<0||ny>=height)continue;
          int next=ny*width+nx;
          if(!seen[next]&&(source.getRGB(nx,ny)>>>24)>=24){seen[next]=true;queue[tail++]=next;}
        }
      }
      if(tail>largest.length)largest=java.util.Arrays.copyOf(queue,tail);
    }
    if(largest.length==0)return source;
    int left=width,top=height,right=0,bottom=0;
    for(int pos:largest){left=Math.min(left,pos%width);right=Math.max(right,pos%width);top=Math.min(top,pos/width);bottom=Math.max(bottom,pos/width);}
    int pad=3;
    BufferedImage result=new BufferedImage(right-left+1+2*pad,bottom-top+1+2*pad,BufferedImage.TYPE_INT_ARGB);
    // Retain soft antialias pixels immediately adjacent to the connected silhouette.
    for(int pos:largest){int x=pos%width,y=pos/width;
      for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
        int sx=x+dx,sy=y+dy,tx=sx-left+pad,ty=sy-top+pad;
        if(sx>=0&&sx<width&&sy>=0&&sy<height&&tx>=0&&tx<result.getWidth()&&ty>=0&&ty<result.getHeight())result.setRGB(tx,ty,source.getRGB(sx,sy));
      }
    }
    return result;
  }
}
