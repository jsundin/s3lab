package s3mock;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import java.io.File;

/**
 * @author johdin
 * @since 2017-12-01
 */
public class S3NinjaLab {
  public static void main(String[] args) {
    AwsClientBuilder.EndpointConfiguration endpoint = new AwsClientBuilder.EndpointConfiguration("http://localhost:9444/s3", "us-west-2");
    AmazonS3 client = AmazonS3ClientBuilder
        .standard()
        .withEndpointConfiguration(endpoint)
        .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")))
        .disableChunkedEncoding()
        .withPathStyleAccessEnabled(true)
        .build();

    //client.createBucket("testbucket");
    client.putObject("testbucket", "file/name", "content");
    //client.putObject("testbucket", "dir/filename", new File("/tmp/s3ninja-2.7.jar"));
  }
}
