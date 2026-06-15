Title: Live Content

Description: Fetched live

Source: https://www.cbse.gov.in/cbsenew/question-paper.html

---

<!DOCTYPE html>
<html lang="en">

<head>
    <!--========= Basic Page Needs =========-->
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <!--========== Specific Meta ==========-->
    <meta name="description" content="Online Education template Based on HTML5.">
    <meta name="keywords" content="HTML5, Template, Design, Development, education, edulab, online cources, training, online education, best education template">
    <!--======== Page Title===========-->
    <title>Previous Years' Question Papers | Central Board of Secondary Education</title>
    <link rel="icon" type="image/x-icon" href="images/cbse_logo.png">
    <!--========== Favicons =========-->
    <!--======== Font icon Css ============-->
    <link href="font-awesome-4.7.0/css/font-awesome.min.css" rel="stylesheet">
    <link href="css/themify-icons.css" rel="stylesheet">
    <!--======= Bootstrap Main Css =============-->
    <link href="css/bootstrap.min.css" rel="stylesheet">
    <!--====== Plugins Css ================-->
    <link href="css/plugins.css" rel="stylesheet">
    <!--====== Custom CSS for themes =======-->
    <link href="css/style.css" rel="stylesheet">
    <link rel="stylesheet" type="text/css" href="css/css1/default.css" />
    <link rel="stylesheet" type="text/css" href="css/css1/component.css" />
    <link rel="stylesheet" type="text/css" href="css/newNavStyle.css" />
    <script src="js/js2/modernizr.custom.js"></script> <!--  images before media-->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css">
    <script>
        function showTime(){
            var date = new Date();
            var h = date.getHours(); // 0 - 23
            var m = date.getMinutes(); // 0 - 59
            var s = date.getSeconds(); // 0 - 59
                var session = "PM";
                
                if(h == 0){
                    h = 12;
                }
                
                if(h > 12){
                   
                    session = "PM";
                }
                if(h < 12) {
            session = "AM"
            }
            
            h = (h < 10) ? "0" + h : h;
            m = (m < 10) ? "0" + m : m;
            s = (s < 10) ? "0" + s : s;
            
            var time = h + ":" + m + ":" + s + " " + session;
            document.getElementById("MyClockDisplay").innerText = time;
            document.getElementById("MyClockDisplay").textContent = time;
            
            setTimeout(showTime, 1000);
            
        }
        
    </script>
    <script>
        function showSlides(n) {
          var i;
          var slides = document.getElementsByClassName("mySlides");
          var dots = document.getElementsByClassName("demo");
          var captionText = document.getElementById("caption");
          if (n > slides.length) {slideIndex = 1}
          if (n < 1) {slideIndex = slides.length}
          for (i = 0; i < slides.length; i++) {
              slides[i].style.display = "none";
          }
          for (i = 0; i < dots.length; i++) {
              dots[i].className = dots[i].className.replace(" active", "");
          }
          slides[slideIndex-1].style.display = "block";
          dots[slideIndex-1].className += " active";
          captionText.innerHTML = dots[slideIndex-1].alt;
        }
    </script>
    <script type="text/javascript">
        function confirm_alert(node) {
            return confirm("This link will take you to an external website. \nयह लिंक आपको एक बाहरी वेबसाइट पर ले जायेगा | ");
        }
    </script>
    <style>
        *[tabindex]:focus {
         border: solid rgb(34, 34, 34) 1px;
        }
    </style>
    <style>
        input[type=text] {
        width: 30px;
        box-sizing: border-box;
        border: 0px solid #f8f9fa;
        border-radius: 4px;
        font-size: 16px;
        background-image: url('img//searchicon.png');
        background-color: #fff;
        background-position: 10px 10px; 
        background-repeat: no-repeat;
        padding: 12px 20px 12px 40px;
        -webkit-transition: width 0.4s ease-in-out;
        transition: width 0.4s ease-in-out;
        
        }
        
        input[type=text]:focus {
          
        width: 100%;
        }
    </style>
</head>

<body onload="showTime()" id="fontSize">
    <!--code goes here-->
    <!--Start code for top strip header-->
    <section class="top-header">
        <div class="container-fluid">
            <div id="myTopSection" class="row align-items-center">
                <!----Top Header---->
            </div>
        </div>
    </section>
    <!--End code for top strip header-->
    <!--====Header Area
  ====================================-->
    <!--copy starts here -->
    <header id="site-header" class="header-area">
        <div class="header-inner">
            <div class="container-fluid">
                <div class="row">
                    <div class="col-lg-12">
                        <div id="myHeaderLogo" class="logo-menu-wrap hidden-xs hidden-sm">
                                <!---Header Logo----->
                        </div>
                    </div>
                            
                </div>
                <div class="row navigation" id="skipCont">
                    <div class="col-lg-12">
                        <nav id="myNewNav" class="navbar navbar-expand-lg myNewNavMain"> <a class="navbar-brand" href="#"></a> <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarNavDropdown" aria-controls="navbarNavDropdown" aria-expanded="false" aria-label="Toggle navigation"> <span class="navbar-toggler-icon"></span> </button>
                            <div class="collapse navbar-collapse" id="navbarNavDropdown">
                                <!--Header Navigation-->
                            </div>
                        </nav>
                    </div>
                </div>
            </div>
        </div>
        <div id="sticky-header"></div>
        <div class="mobile-menu myMobMenuMain"> <a class="mobile-logo" href="#"> <img src="img/cbse-logo-with-name.png" alt="CBSE logo" style="height:40px; width:200px;"> </a> </div>
    </header>
    <!--/.header-area-->
    <!-- Page Content -->
    <div class="courses-page page-wrapper">
        <div class="courses-page-content">            
            <div class="container-fluid">
                <div class="row">
                    <div class="col-lg-12 col-sm-12 col-xs-12 breadcrumb" tabindex="0">Question Paper for Examination</div>
                </div>
            </div>
            <!--tab section start here-->
            <div class="empty-space"> </div>
            <div class="container">               
                <div id="accordionExample">
                    <div class="accordCardMainNew">
                        <div id="headingNine">
                          <button class="btn btn-link btn-block accordion collapsed" type="button" data-toggle="collapse" data-target="#collapseNine" aria-expanded="true" aria-controls="collapseNine">
                            Question Papers for Examination 2026
                          </button>
                        </div>
                        <div id="collapseNine" class="collapse" aria-labelledby="headingNine" data-parent="#accordionExample">
                          <div class="card-body">
                            <div class="subAccordionMain" id="sub-accordionExample">
                              <div class="accordCardMainNew">
                                  <div id="sub-headingSeventeen">
                                      <button class="btn btn-link btn-block accordion collapsed" type="button" data-toggle="collapse" data-target="#sub-collapseSeventeen" aria-expanded="true" aria-controls="sub-collapseSeventeen">
                                        Class XII
                                      </button>
                                  </div>
                                  <div id="sub-collapseSeventeen" class="accordion-collapse collapse" aria-labelledby="sub-headingSeventeen" data-parent="#sub-accordionExample">
                                      <div class="card-body">
                                        <h2 style="font-size:23px;color:#000; text-align: center;">Question Paper for Class XII Examination 2026</h2>
                                        <table class="TFtable" role="presentation">
                                            <tr>
                                                <th style="text-align:center; background-color: #428bca;">
                                                    <h2 style="font-size:15px;color:#fff;">SUBJECT NAME</h2>
                                                </th>
                                                <th style="text-align:center; background-color: #428bca;">
                                                    <h2 style="font-size:15px;color:#fff;">DOWNLOAD</h2>
                                                </th>
                                                <th style="text-align:center; background-color: #428bca;">
                                                    <h2 style="font-size:15px;color:#fff;">FILE TYPE</h2>
                                                </th>
                                                <th style="text-align:center; background-color: #428bca;">
                                                    <h2 style="font-size:15px;color:#fff;">FILE SIZE</h2>
                                                </th>
                                            </tr>
                                            <tr>
                                                <td>ACCOUNTANCY </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Accountancy.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>57.8 MB</td>
                                            </tr>
                                            <tr>
                                                <td>AGRICULTURE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Agriculture.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>466 KB</td>
                                            </tr>
                                            <tr>
                                                <td>AIR-CONDITIONING & REFRIGERATION</td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Air_Conditionng_Refrigeration.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>638 KB</td>
                                            </tr>
                                            <tr>
                                                <td>APPLIED MATHEMATICS</td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Applied_Math.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>623 KB</td>
                                            </tr>
                                            <tr>
                                                <td>ARABIC</td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Arabic.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>5.14 MB</td>
                                            </tr>
                                            <tr>
                                                <td>ARTIFICIAL INTELLIGENCE</td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Artificial_Intelligence.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>341 KB</td>
                                            </tr>
                                            <tr>
                                                <td>ASSAMESE</td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Assamese.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>2.30 MB</td>
                                            </tr>
                                            <tr>
                                                <td>AUTOMOTIVE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Automotive.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>561 KB</td>
                                            </tr>
                                            <tr>
                                                <td>BANKING </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Banking.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>530 KB</td>
                                            </tr>
                                            <tr>
                                                <td>BEAUTY & WELLNESS </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Beauty_Wellness.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>841 KB</td>
                                            </tr>
                                            <tr>
                                                <td>BENGALI </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Bengali.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>2.35 MB</td>
                                            </tr>
                                            <tr>
                                                <td>BHARATNATYAM - DANCE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Bharatanatyam_Dance.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>498 KB</td>
                                            </tr>
                                            <tr>
                                                <td>BHUTIA </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Bhutia.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>3.85 MB</td>
                                            </tr>
                                            <tr>
                                                <td>BIOLOGY </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Biology.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>16.4 MB</td>
                                            </tr>
                                            <tr>
                                                <td>BIOTECHNOLOGY </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Biotechnology.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>830 KB</td>
                                            </tr>                                            
                                            <tr>
                                                <td>BUSSINESS STUDIES </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Business_Studies.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>11.8 MB</td>
                                            </tr>
                                            <tr>
                                                <td>BUSINESS ADMINISTRATION </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Business_Administration.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>905 KB</td>
                                            </tr>
                                            <tr>
                                                <td>BHOTI</td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Bhoti.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>2.33 MB</td>
                                            </tr>
                                            <tr>
                                                <td>CARNATIC MUSIC VOCAL </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Carnatic_Music_Vocal.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>490 KB</td>
                                            </tr>
                                            <tr>
                                                <td>CARNATIC MUSIC MEL INS </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Carnatic_Music_Melodic_Instru.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>496 KB</td>
                                            </tr>
                                            <tr>
                                                <td>CARNATIC MUSIC PERC INS </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Carnatic_Music_Instl.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>498 KB</td>
                                            </tr>
                                            <tr>
                                                <td>CHEMISTRY </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Chemistry.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>19.9 MB</td>
                                            </tr>
                                            <tr>
                                                <td>COMMERCIAL ART </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Commercial_Art_Theory.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>2.40 MB</td>
                                            </tr>
                                            <tr>
                                                <td>COMPUTER SCIENCE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Computer_Science.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>1.20 MB</td>
                                            </tr>
                                            <tr>
                                                <td>COST ACCOUNTING </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Cost_Accounting.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>624 KB</td>
                                            </tr>                                           
                                            <tr>
                                                <td>DATA SCIENCE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Data_Science.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>901 KB</td>
                                            </tr>
                                            <tr>
                                                <td>DESIGN THINKING AND INNOVATION</td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Design_Thinking_Innovation.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>811 KB</td>
                                            </tr>
                                            <tr>
                                                <td>EARLY CHILDHOOD CARE & EDUCATION </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Early_Childhood_Care_Educaiton.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>507 KB</td>
                                            </tr>
                                            <tr>
                                                <td>ECONOMICS </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Economics.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>14.0 MB</td>
                                            </tr>
                                            <tr>
                                                <td>ELECTRICAL TECHNOLOGY </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Electrical_Technology.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>564 KB</td>
                                            </tr>
                                            <tr>
                                                <td>ELECTRONIC TECHNOLOGY </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Electronics_Technology.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>502 KB</td>
                                            </tr>
                                            <tr>
                                                <td>ELECTRONICS AND HARDWARE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Electronics_Hardware.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>2.84 MB</td>
                                            </tr>
                                            <tr>
                                                <td>ENGLISH CORE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/ENGLISH_CORE.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>15.9 MB</td>
                                            </tr>
                                            <tr>
                                                <td>ENGLISH ELECTIVE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/English_Elective.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>436 KB</td>
                                            </tr>
                                            <tr>
                                                <td>ENGINEERING GRAPHICS </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Engg_Graphics.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>1.33 MB</td>
                                            </tr>                                            
                                            <tr>
                                                <td>ENTREPRENEURSHIP </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Entrepreneurship.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>1.18 MB</td>
                                            </tr>
                                            <tr>
                                                <td>FASHION STUDIES </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Fashion_Studies.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>994 KB</td>
                                            </tr>
                                            <tr>
                                                <td>FINANCIAL MARKETS MANAGEMENT </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Financial_Markets_Management.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>525 MB</td>
                                            </tr>
                                            <tr>
                                                <td>FOOD NUTRITION & DIETETICS </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Food_Nutrition.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>272 KB</td>
                                            </tr>
                                            <tr>
                                                <td>FOOD PRODUCTION </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Food_Production.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>616 KB</td>
                                            </tr>
                                            <tr>
                                                <td>FRENCH </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/French.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>348 KB</td>
                                            </tr>
                                            <tr>
                                                <td>FRONT OFFICE OPERATION </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Front_Office_Operations.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>900 KB</td>
                                            </tr>
                                            <tr>
                                                <td>GEOGRAPHY </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Geography.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>53.2 MB</td>
                                            </tr>
                                            <tr>
                                                <td>GERMAN </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/German.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>381 KB</td>
                                            </tr>
                                            <tr>
                                                <td>GRAPHICS </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Graphics.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>1.16 MB</td>
                                            </tr>
                                            <tr>
                                                <td>GUJARATI </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Gujarati.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>629 KB</td>
                                            </tr>
                                            <tr>
                                                <td>GEOSPATIAL TECHNOLOGY</td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Geospatial_technology.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>891 KB</td>
                                            </tr>
                                            <tr>
                                                <td>HEALTH CARE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Health_Care.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>607 KB</td>
                                            </tr>
                                            <tr>
                                                <td>HINDI CORE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Hindi_Core.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>12.0 MB</td>
                                            </tr>
                                            <tr>
                                                <td>HINDI ELECTIVE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Hindi_Elective.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>12.4 MB</td>
                                            </tr>
                                            <tr>
                                                <td>HINDUSTANI MUSIC MEL INS </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Hindustani_Music_Melodic_Insmts.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>567 KB</td>
                                            </tr>
                                            <tr>
                                                <td>HINDUSTANI MUSIC PER INS </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Hindustani_Music_Percussion_Inst.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>560 KB</td>
                                            </tr>
                                            <tr>
                                                <td>HISTORY </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/History.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>14.4 MB</td>
                                            </tr>
                                            <tr>
                                                <td>HOME SCIENCE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Home Science.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>726 KB</td>
                                            </tr>
                                            <tr>
                                                <td>HORTICULTURE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Horticulture.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>589 KB</td>
                                            </tr>
                                            <tr>
                                                <td>INFORMATICS PRACTICE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Informatics_Practices.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>0.98 MB</td>
                                            </tr>
                                            <tr>
                                                <td>INFORMATION TECHNOLOGY </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Information_Technology.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>896 KB</td>
                                            </tr>
                                            <tr>
                                                <td>INSURANCE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Insurance.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>608 KB</td>
                                            </tr>
                                            <tr>
                                                <td>JAPANESE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Japanese.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>2.08 MB</td>
                                            </tr>
                                            <tr>
                                                <td>KANNADA </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Kannada.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>3.60 MB</td>
                                            </tr>
                                            <tr>
                                                <td>KATHAK DANCE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Kathak_dance.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>1.25 MB</td>
                                            </tr>
                                            <tr>
                                                <td>KOKBOROK </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Kokborok.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>2.0 MB</td>
                                            </tr>
                                            <tr>
                                                <td>KATHAKALI DANCE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Kathakali_Dance.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>784 MB</td>
                                            </tr>
                                            <tr>
                                                <td>KNOWLEDGE TRADITIONS AND PRACTICES OF INDIA</td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Knowledge_Tradubg.zip">Download</a></td>
                                                <td class="text-center"><i class="fa fa-file-archive-o" aria-hidden="true" style="color:#800000; font-weight: 700;" title="ZIP File"></i></td>
                                                <td>1.63 MB</td>
                                            </tr>
                                            <tr>
                                                <td>KUCHIPUDI - DANCE </td>
                                                <td><a target="_blank" href="question-paper/2026/XII/Kuchipudii_Dance.zip">Download</a></td>
                       

