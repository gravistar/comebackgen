package util;

import java.io.File;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
public class Crawler{
    public static List<File> getFilesInDir(File startingDir) {
    	Set<String> seenPaths = Sets.newHashSet();
    	List<File> files = Lists.newArrayList();
    	PriorityQueue<File> q = Queues.newPriorityQueue();
    	q.add(startingDir);
    	while (!q.isEmpty()) {
    		File curFile = q.remove();
    		if (curFile.isFile())
    			files.add(curFile);
    		
    		if (curFile.listFiles() == null) continue;
    		
    		File[] children = curFile.listFiles();
    		for(File child : children) {
    			if (!seenPaths.contains(child.getPath()))
    				q.add(child);
    		}
    	}
    	return files;
    }
}