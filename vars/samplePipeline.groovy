/*************************************************************************
**** Description :: This groovy code is used to run the ROR pipeline  ****
**** Created By  :: Pramod Vishwakarma                                ****
**** Created On  :: 08/02/2018                                        ****
**** version     :: 1.0                                               ****
**************************************************************************/
import com.symantec.devops.scm.*
import com.symantec.devops.build.java.*
import com.symantec.devops.reports.*
import com.symantec.devops.notification.*
//import com.symantec.devops.deploy.*
//import com.symantec.devops.approval.*

def call(body) 
{
   def config = [:]
   body.resolveStrategy = Closure.DELEGATE_FIRST
   body.delegate = config
   body()
   timestamps {
     try {
        def ruby = new RubyOnRails()
        ruby.createReportDirectory("${config.REPORT_DIRECTORY}")
        def html = new htmlReport()
        currentBuild.result = "SUCCESS"
        NEXT_STAGE = "none"
        stage ('\u2776 Code Checkout') {
           def git = new git()
           git.Checkout("${config.GIT_URL}","${BRANCH}","${config.GIT_CREDENTIALS}")
           NEXT_STAGE="Java_best_practice"
        }
	    stage ('\u2777 Pre-Build Tasks') {
           parallel (
              "\u2460 Check Best Practice" : {
                 while (NEXT_STAGE != "Java_best_practice") {
                   continue
                 }
                 ruby.javaBestPractices("${config.REPORT_DIRECTORY}")
                 html.publishHtmlReport("${config.RUBY_REPORT_FILE}","${config.REPORT_DIRECTORY}","${config.RUBY_REPORT_TITLE}")
                 NEXT_STAGE="rubocop"
              },
              "\u2461 Static Analysis" : {
                 while (NEXT_STAGE != "rubocop") {
                   continue
                 }
                 ruby.rubyCodeAnalyzer("${config.RUBOCOP_REPORT_FILE}","${config.REPORT_DIRECTORY}")
                 html.publishHtmlReport("${config.RUBOCOP_REPORT_FILE}","${config.REPORT_DIRECTORY}","${config.RUBOCOP_REPORT_TITLE}")
                 NEXT_STAGE="security"
              },
              "\u2462 Security Scan" : {
                 while (NEXT_STAGE != "security") {
                   continue
                 }
                 ruby.scanSecurityVulnerabilities("${config.BRAKEMAN_REPORT_FILE}","${config.REPORT_DIRECTORY}")
                 html.publishHtmlReport("${config.BRAKEMAN_REPORT_FILE}","${config.REPORT_DIRECTORY}","${config.BRAKEMAN_REPORT_TITLE}")
                 NEXT_STAGE='code_smell'
              },
              "\u2463 code smell" : {
                 while (NEXT_STAGE != "code_smell") {
                   continue
                 }
                 ruby.rubyCodeSmell("${config.REPORT_DIRECTORY}")
                 html.publishHtmlReport("${config.RUBYCRITIC_REPORT_FILE}","${config.REPORT_DIRECTORY}","${config.RUBYCRITIC_REPORT_TITLE}")
                 NEXT_STAGE='unit_tests'
              },
              failFast: true
           )
	    }
        stage ('\u2778 Build Tasks') {
          parallel (
            "\u2460 Unit Tests" : {
               while (NEXT_STAGE != "unit_tests") {
                 continue
               }
               if ( "${config.SKIP_UNIT_TEST}" == "no" ) {
                 ruby.rubyUnitTests("${config.RSPEC_REPORT_FILE}","${config.REPORT_DIRECTORY}")
                 html.publishHtmlReport("${config.RSPEC_REPORT_FILE}","${config.REPORT_DIRECTORY}","${config.RSPEC_REPORT_TITLE}")
               }
               NEXT_STAGE='clean_package'
            },
       stage('\u2779 Post-Build Tasks') {
         parallel (
           "\u2461 Deployment Alert" : {
             while (NEXT_STAGE != 'send_alert') {
               continue
             }
             def e = new email()
             e.sendDeployEmail("${config.BRAND_NAME}","${ENVIRONMENT}")
           },
     catch (Exception caughtError) {
        wrap([$class: 'AnsiColorBuildWrapper']) {
            print "\u001B[41mERROR => symantec pipeline failed, check detailed logs..."
            currentBuild.result = "FAILURE"
            throw caughtError
        }
     }
     finally {
         def g = new git()
         g.notifyBitbucket("${config.BITBUCKET_NOTIFY_URL}","${currentBuild.result}")
         def e = new email()
         String BODY = new File("${WORKSPACE}/${config.EMAIL_TEMPLATE}").text
       e.sendemail("${currentBuild.result}","$BODY","${config.RECIPIENT}","${ENVIRONMENT}")
     }

   }
}

