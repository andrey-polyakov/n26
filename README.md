#MAIN IDEA

In essence, the main idea behind this is to split time window you want to track into intervals so called buckets. Then as time goes buckets expire. For this purpose a data structure called Circular Buffer is used.

In order to compute a snopshot, values from all buckets need to be aggregated. Of course, under heavy contention numbers may be lagging. 

#HOW TO RUN
Just launch com.n2.Main class. You can also give it a try using IntegraionTest.

#KNOWN ISSUES

The service accepts future stamped values. 
