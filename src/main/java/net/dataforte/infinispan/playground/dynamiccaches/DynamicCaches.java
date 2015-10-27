package net.dataforte.infinispan.playground.dynamiccaches;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.locks.LockSupport;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.distexec.DefaultExecutorService;
import org.infinispan.distexec.DistributedCallable;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

public class DynamicCaches {

   public static void main(String[] args) throws Exception {
      int expectedNodeCount = (args.length == 0) ? 2 : Integer.parseInt(args[0]);
      GlobalConfigurationBuilder global = GlobalConfigurationBuilder.defaultClusteredBuilder();
      ConfigurationBuilder config = new ConfigurationBuilder();
      config.clustering().cacheMode(CacheMode.DIST_SYNC);
      DefaultCacheManager cm = new DefaultCacheManager(global.build(), config.build());
      Cache<?, ?>  defaultCache = cm.getCache();
      if (cm.isCoordinator()) {
         System.out.printf("Waiting for %d nodes...", expectedNodeCount);
         while (cm.getClusterSize() < expectedNodeCount) {
            Thread.sleep(1000);
         }

         DefaultExecutorService des = new DefaultExecutorService(defaultCache);
         List<Future<Void>> results = des.submitEverywhere(new DistributedCallable<Object, Object, Void>() {

            private EmbeddedCacheManager cacheManager;

            @Override
            public Void call() throws Exception {
               // Create the configuration on every node
               ConfigurationBuilder builder = new ConfigurationBuilder();
               builder.clustering().cacheMode(CacheMode.DIST_SYNC);
               cacheManager.defineConfiguration("mycache", builder.build());
               // Retrieve the cache to create it
               cacheManager.getCache("mycache");
               return null;
            }

            @Override
            public void setEnvironment(Cache<Object, Object> cache, Set<Object> inputKeys) {
               cacheManager = cache.getCacheManager();
            }
         });
         for(Future<Void> result : results) {
            result.get();
         }

      }  else {
         System.out.println("Slave node waiting... Ctrl-C to exit.");
         LockSupport.park();
      }

      cm.stop();
   }
}
