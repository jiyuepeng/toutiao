package com.jyp.service;

import com.jyp.dao.NewsDAO;
import com.jyp.model.News;
import com.jyp.util.RedisKeyUtil;
import com.jyp.util.ToutiaoUtil;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class NewsService {
    @Autowired
    NewsDAO newsDAO;

    @Autowired
    JedisAdapter jedisAdapter;

    public int addNews(News news)
    {
        newsDAO.addNews(news);
        setNewsScore(news);
        return news.getId();
    }


    public void updateCommentCount(int id,int commentCount)
    {
        newsDAO.updateCommentCount(commentCount,id);
        setNewsScore(newsDAO.selectById(id));
    }

    public List<News> getLatestNewsByUserId(int userId, int offset, int limit)
    {
        return newsDAO.selectByUserIdAndOffset(userId, offset, limit);
    }
    public String addImage(MultipartFile file) throws IOException
    {
        int pos = file.getOriginalFilename().lastIndexOf(".");
        if(pos <0)
        {
            return null;
        }
        String fileindex = file.getOriginalFilename().substring(pos + 1).toLowerCase();
        if(!ToutiaoUtil.isSaveImage(fileindex))
        {
            return null;
        }
        String saveFileName = UUID.randomUUID().toString().replace("-","")+ "." + fileindex;
        Files.copy(file.getInputStream(),new File(ToutiaoUtil.IMAGE_DIR + saveFileName).toPath(), StandardCopyOption.REPLACE_EXISTING);
        return ToutiaoUtil.TOUTIAO_DOMAIN+"image?name="+saveFileName;

    }
    public void addHotNews(News news)
    {
        String hotNewsKey = RedisKeyUtil.getHotNewsKey();


    }
    public List<News> getHotNews(int count)
    {
        String hotNewsKey = RedisKeyUtil.getHotNewsKey();
        List<Integer> hotNewsIdList = getIdsFromSet(jedisAdapter.zrevrange(hotNewsKey, 0, count));
        List<News> hotNewsList = new ArrayList<News>();
        for(Integer newsId : hotNewsIdList)
        {
            hotNewsList.add(newsDAO.selectById(newsId));
        }
        return hotNewsList;
    }
    public News getNewsById(int id)
    {
        return newsDAO.selectById(id);
    }

    public void updateLikeCount(int id,int likeCount)
    {
        newsDAO.updateLikeCount(likeCount,id);
        setNewsScore(newsDAO.selectById(id));
    }

    public double getNewsScore(News news)
    {
        int commentCount = news.getCommentCount();
        int likeCount = news.getLikeCount();
        Date date = news.getCreatedDate();
        Date nowDate = new Date();
        long l=nowDate.getTime()-date.getTime();
        long day=l/(24*60*60*1000);
        long hour=(l/(60*60*1000)-day*24);
        long g = 2;
        double score = (commentCount+likeCount+1)/Math.pow(hour,g);
        return hour;
    }
    public void setNewsScore(News news)
    {
        double score =getNewsScore(news);
        String hotNewsKey = RedisKeyUtil.getHotNewsKey();
        Jedis jedis = jedisAdapter.getJedis();
        Transaction tx = jedisAdapter.multi(jedis);
        tx.zadd(hotNewsKey, score, String.valueOf(news.getId()));
        List<Object> ret = jedisAdapter.exec(tx, jedis);
    }
    private List<Integer> getIdsFromSet(Set<String> idset) {
        List<Integer> ids = new ArrayList<Integer>();
        for (String str : idset) {
            ids.add(Integer.parseInt(str));
        }
        return ids;
    }
    public News getById(int id)
    {
        return newsDAO.selectById(id);
    }

    public List<News> getAll()
    {
        return newsDAO.getAll();
    }
}
