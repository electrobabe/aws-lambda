# Electrobabe's Utils - Calendar Utils

You are here: https://github.com/electrobabe/aws-lambda

* Author: barbara ondrisek
* Website: https://electrobabe.at
* Contact: Don't contact me, I'll contact you! :P

## Description

This is a pet project to read in an ics file and display the calendar values of a specific day as a text list

Usage:
* Enter ICS / Webcal file, e.g. https://outlook.office365.com/owa/calendar/123/calendar.ics
* Enter Date: formal yyyy-MM-dd
* Returns a list of events with:
  * Duration, e.g. 0h 30m
  * Event name: e.g. "Daily Stand-up"
  * Type: e.g. BUSY, TENTATIVE, OOF (out of office)

## Tech Stack

- FE: plain HTML, not even CSS ;)
- BE Language: Java 11
- Hosting: BE on AWS Lambda

## Deploy

    mvn clean install

-> deploy to AWS Lambda function

## AWS Lambda Setup How To

(!) Disclaimer: Might be outdated

AWS setup steps:
https://docs.aws.amazon.com/apigateway/latest/developerguide/integrating-api-with-aws-services-lambda.html

### 1. create lambda function

https://[AWS_REGION].console.aws.amazon.com/lambda/home?region=[AWS_REGION]#/functions/[FUNCTION_NAME]


### 2. create new user:

https://console.aws.amazon.com/iam/home?region=[AWS_REGION]#/users
- User: lambda_user
- Access key ID: [ACCESS_KEY_ID]
- Secret access key: [SECRET_ACCESS_KEY]
- ARN user: [ARN_USER]

add in local ~/.aws/config


### 3. configure

https://[AWS_REGION].console.aws.amazon.com/lambda/home?region=[AWS_REGION]#/functions/[FUNCTION_NAME]?newFunction=true&tab=configure

set region in local AWS config file ~/.aws/config


### 4. publish version

https://[AWS_REGION].console.aws.amazon.com/lambda/home?region=[AWS_REGION]#/functions/[FUNCTION_NAME]/versions/1?tab=configure


### 5. add trigger: API Gateway

- Create a REST API.
  - API endpoint https://[API_GATEWAY_ID].execute-api.[AWS_REGION].amazonaws.com/default/[FUNCTION_NAME]

- API Gateway:
  - Gateway ARN: arn:aws:execute-api:[AWS_REGION]:[LAMBDA_ID]:[API_GATEWAY_ID]/*/*/[FUNCTION_NAME]

configure trigger: 
- https://[AWS_REGION].console.aws.amazon.com/lambda/home?region=[AWS_REGION]#/functions/[FUNCTION_NAME]?tab=configure
- https://[AWS_REGION].console.aws.amazon.com/apigateway/home?region=[AWS_REGION]#/apis/[API_GATEWAY_ID]/resources/[GATEWAY_RESOURCE]/methods/ANY

- REST API https://[API_GATEWAY_ID].execute-api.[AWS_REGION].amazonaws.com/default/[FUNCTION_NAME]


### 6. Optional: add VPC

- give Lambda the required network interface related permissions in a VPC
  - ec2:DescribeNetworkInterfaces 
  - ec2:CreateNetworkInterface 
  - ec2:DeleteNetworkInterface
- execution role "Add permissions to [FUNCTION_NAME]-role-[ROLE_ID]": 
  - https://console.aws.amazon.com/iam/home?#/roles/[FUNCTION_NAME]-role-[ROLE_ID]?section=permissions
- "Create policy"

        {
          "Version": "2012-10-17",
          "Statement": [
            {
              "Effect": "Allow",
              "Action": [
                "ec2:DescribeNetworkInterfaces",
                "ec2:CreateNetworkInterface",
                "ec2:DeleteNetworkInterface",
                "ec2:DescribeInstances",
                "ec2:AttachNetworkInterface"
              ],
              "Resource": "*"
            }
          ]
        }

- see https://stackoverflow.com/questions/41177965/aws-lambdathe-provided-execution-role-does-not-have-permissions-to-call-describ
  - set name "EC2_NetworkInterface_Permissions"
  - go to lambda role, "Permissions policies", "attach policies", https://console.aws.amazon.com/iam/home#/roles/[FUNCTION_NAME]-role-[ROLE_ID]$addPermissions?step=policy
next to "AWSLambdaBasicExecutionRole-[LAMBDA_EXECUTION_ROLE_ID]"


- setup VPC - virtual private cloud: 
https://[AWS_REGION].console.aws.amazon.com/lambda/home?region=[AWS_REGION]#/functions/[FUNCTION_NAME]?tab=configure
-- use all 3 subnets, add VPC with security group (e.g.	Security group ID: [SECURITY_GROUP_ID]) and "inbound rules" and "outbound rules", make sure "0.0.0.0/0, ::/0" is set for PORT 80)


see also https://aws.amazon.com/de/premiumsupport/knowledge-center/internet-access-lambda-function/

- Details:
  - Lambda Function ARN: arn:aws:lambda:[AWS_REGION]:[LAMBDA_ID]:function:[FUNCTION_NAME]  
  - API Gateway: apigateway.amazonaws.com: ARN: arn:aws:execute-api:[AWS_REGION]:[LAMBDA_ID]:[API_GATEWAY_ID]/*/*/[FUNCTION_NAME]
  - User ARN: arn:aws:iam::[LAMBDA_ID]:user/lambda_user
  - Lambda Function Execution Role ARN: arn:aws:iam::[LAMBDA_ID]:role/service-role/[FUNCTION_NAME]-role-[ROLE_ID]

- Role ARN "lambda_basic_execution": arn:aws:iam::[LAMBDA_ID]:role/lambda_basic_execution -  lambda_basic_execution
https://console.aws.amazon.com/iam/home#/roles/lambda_basic_execution?section=permissions



  
### Known issues:

- ERROR "Unable to load region from any of the providers in the chain software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain@4dc27487: [software.amazon.awssdk.regions.providers.SystemSettingsRegionProvider@1573f9fc: Unable to load region from system settings. Region must be specified either via environment variable (AWS_REGION) or  system property (aws.region)., software.amazon.awssdk.regions.providers.AwsProfileRegionProvider@bccb269: No region provided in profile: default, software.amazon.awssdk.regions.providers.InstanceProfileRegionProvider@7fb4f2a9: Unable to contact EC2 metadata service.]"

-> set in ~/.aws/config (not env var: set AWS_REGION=[AWS_REGION])
region = [AWS_REGION]

- ERROR "User: arn:aws:sts::[LAMBDA_ID]:assumed-role/[FUNCTION_NAME]-role-[ROLE_ID]/[FUNCTION_NAME] is not authorized to perform: lambda:GetAccountSettings on resource: * (Service: Lambda, Status
  Code: 403, Request ID: dbc74a3c-3ded-491d-bc5d-d44ff78a65f0)"

--> https://stackoverflow.com/questions/37498124/accessdeniedexception-user-is-not-authorized-to-perform-lambdainvokefunction

- ERROR: calling through API gateway, receiving a 502 {"message": "Internal server error"} through URL call --> check test invocation on AWS: https://[AWS_REGION]
  .console.aws.amazon.com/apigateway/home?region=[AWS_REGION]#/apis/[API_GATEWAY_ID]/resources/[GATEWAY_RESOURCE]/methods/ANY

+ check logs: "Execution failed due to configuration error: Malformed Lambda proxy response. Method completed with status: 502"
  -> https://aws.amazon.com/de/premiumsupport/knowledge-center/malformed-502-api-gateway/

+ set timeout to 90sec

# Housekeeping

## Maven Dependency Cleanup

### Analyse the whole dependency tree

    mvn dependency:tree -Ddetail=true

### Check unused dependencies:

    mvn dependency:analyze -DignoreNonCompile

### look for mismatches in your dependencyManagement section.

    mvn dependency:analyze-dep-mgt 

Info:

- Dependencies with runtime or provided scope will flag up as "Unused declared" unless you use the ignoreNonCompile flag when analysing dependencies
- Used undeclared dependencies are those which are required, but have not been explicitly declared as dependencies in your project. They are however available thanks to transitive dependency of other
  dependencies in your project. It is a good idea to explicitly declare these dependencies. This also allows you to control the version of these dependencies (perhaps matching the version provided by
  your runtime).
- As for unused declared dependencies, it is a good idea to remove them. Why add unnecessary dependency to your project? But then transitivity can bring these in anyway, perhaps, conflicting with your
  runtime versions. In this case, you will need to specify them — essentially to control the version.
- By the way, mvn dependency:tree gives the dependency tree of the project, which gives you a better perspective of how each dependency fits in in your project.

# TODOs

- implement basic captcha https://allwebco-templates.com/support/script-simple-captcha.htm