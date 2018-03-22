package hello;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import redis.clients.jedis.Jedis;

/*import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableAsync;*/

@Configuration
//@ComponentScan
//@EnableAsync
public class MyConfiguration {
	
	/*@Bean
	public static JedisConnectionFactory jedisConnectionFactory() {
		JedisConnectionFactory jedisConFactory = new JedisConnectionFactory();
		jedisConFactory.setHostName("localhost");
	    jedisConFactory.setPort(6379);
	    return jedisConnectionFactory();
	}
	 
	@Bean
	public static RedisTemplate<String, Object> redisTemplate() {
	    RedisTemplate<String, Object> template = new RedisTemplate<String, Object>();
	    template.setConnectionFactory(jedisConnectionFactory());
	    return template;
	}*/

	@Bean
	
	public static Jedis myJedis(){
		Jedis jedis = new Jedis();
		return jedis;
		
	}
	
}
