Title: Live Content

Description: Fetched live

Source: https://cbseacademic.nic.in

---

﻿<!DOCTYPE html>
<html>
<head>
<META NAME="Keywords" CONTENT="cbseacad, Central Board of Secondary Education, Academic, Academic Unit, Academics Unit,CBSE Academic Unit, CBSE Website, CBSE Acad, CBSE New Delhi, Education Board, CBSE, Shiksha Sadan, 17, Rouse Avenue, New Delhi 110002, India">
<meta name="google-site-verification" content="RU_DQWObUgJ-UhqQJ-PhmkDu23BdFl7Zb-bKgEU6TyU" />
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>CBSE | Central Board of Secondary Education : Academics</title>
<link type="text/css" href="resource/css/new_main.css" rel="stylesheet" />
<script src="resource/js/jquery.min.js"></script>
<link href="resource/bootstrap5/css/bootstrap.min.css" rel="stylesheet">
<script src="resource/bootstrap5/js/bootstrap.bundle.min.js"></script>
<script>
jQuery(document).ready(function($){
	var hasQueryString = document.URL.indexOf('=hindi');
	if (hasQueryString != -1)
	{
        $('span.english').hide();

        $( "a.lang") .each(function() {
          $(this).attr("href", $(this).attr("href")+"?lang=hindi");
        });

        //$('a.lang').attr("href", $('a.lang').attr("href")+"?lang=hindi");
	}else{
        $('span.hindi').hide();	
	}
    //});
});

$(document).ready(function() {
            $("#tabsnav span#tab1, #tabsnav span#tab2").click(function() { 
                //e.preventDefault();	
                $(".tabcontent").hide();
                $("#tabsnav span").removeClass("active");
                $(this).addClass("active");
                $("#" + this.id + "_ans").show();
            });
        });
</script>
<script type="text/javascript" src="https://cbseacademic.nic.in/resource/js/fishmenu.js"></script>
<script type="text/javascript" src="https://cbseacademic.nic.in/resource/js/contentslider.js"></script>
<script type="text/javascript">
        jQuery(document).on('ready',function($) {

            var id = '#dialog';

            //Get the screen height and width
            var maskHeight = $(document).height();
            var maskWidth = $(window).width();

            //Set heigth and width to mask to fill up the whole screen
            $('#mask').css({
                'width': maskWidth,
                'height': maskHeight
            });

            //transition effect		
            $('#mask').fadeIn(1000);
            $('#mask').fadeTo("slow", 0.8);

            //Get the window height and width
            var winH = $(window).height();
            var winW = $(window).width();

            //Set the popup window to center
            //$(id).css('top',  winH/2-$(id).height()/2);
            $(id).css('top', '0px');
            $(id).css('left', winW / 2 - $(id).width() / 2);

            //transition effect
            //$(id).fadeIn(2000); 	

            //if close button is clicked
            $('.window .close').click(function(e) {
                //Cancel the link behavior
                e.preventDefault();

                $('#mask').hide();
                $('.window').hide();
            });

            //if mask is clicked
            $('#mask').click(function() {
                $(this).hide();
                $('.window').hide();
            });
        });        
    </script>
<script type="text/javascript">
        

        jQuery(window).on('load',function($) {

            var is_manual = true;
            var interval1;
            var interval2;
            var Current_Tab = 1;
            var Current_IMG = 1;
            var Cycle_Speed_Seconds = 12;
            var Cycle_Speed_Slider = 5;
            jQuery(function($) {
                setTimeout(function() {
                    //console.log("start");
                    //set a click handler to your tabs:
                    $('#tab1, #tab2, #tab3').mouseover(function() {
                        console.log("click");
                        //check if clicked manually or automatically:
                        if (is_manual == false) {
                            //if automatic, clear flag and continue
                            is_manual = true;
                        } else if (is_manual == true) {
                            //if manual, clear interval
                            clearInterval(interval1);
                        }

                        //execute default action:
                        return true;
                    });
                    //set the interval to swap between tabs
                    /* interval1 = setInterval(function() {
                        //indicate that the click was trigerred automatically:
                        is_manual = false;
                        if (Current_Tab == 1) {
                            Current_Tab = 2;
                            $('#tab2').trigger('click');
                        } else if (Current_Tab == 2) {
                            Current_Tab = 3;
                            $('#tab3').trigger('click');
                        } else if (Current_Tab == 3) {
                            Current_Tab = 1;
                            $('#tab1').trigger('click');
                        }

                    }, Cycle_Speed_Seconds * 1000); */

                }, 0);
            });     


        });

        
    </script>
<style type="text/css">
.body-container{font-size:12px;}
        .globe1 {
            height: 200px;
            overflow: auto;
            width: 100%;
        }
        
        .masterglobe .globe1 img {
            padding: 0px;
            float: left;
            clear: left;
        }
        
        .masterglobe .globe1 p {
            margin: 0px;
            padding: 0px;
            line-height: 25px;
			font-size:13px;
			font-weight:bold;
        }
        
        #mask {
            position: absolute;
            left: 0;
            top: 0;
            z-index: 9000;
            background-color: #000;
            display: none;
        }
        
        #boxes .window {
            position: absolute;
            left: 0;
            top: 0;
            width: 440px;
            height: 200px;
            display: none;
            z-index: 9999;
            padding: 20px;
        }
        
        #boxes #dialog {
            width: 900px;
            height: 1370px;
            padding: 20px;
            background-color: #ffffff;
        }

		#activitycorner{ border-left:#FAFAD8 1px solid; padding:5px; float:right; width:21%; margin-top:25px; font-family:Arial, Helvetica, sans-serif; height:225px;}
		#activitycorner h3{ color:#BD3037; margin-top:0;}
		#activitycorner p{ line-height:18px;font-size:13px;margin-bottom:5px;}
		#activitycorner a{ color:#000;font-family:Arial, Helvetica, sans-serif;}

		#infocus{ border-left:#f47514 1px solid; padding:5px; float:right; width:21%; margin-top:25px; font-family:Arial, Helvetica, sans-serif; height:225px;}
		#infocus h3{ color:#BD3037; margin-top:0; font-size:14px;}
		#infocus p{ line-height:18px;font-size:13px;margin-bottom:5px;}
		#infocus a{ color:#000;font-family:Arial, Helvetica, sans-serif;}
    </style>
<style type="text/css">
        .blink {
            animation-duration: 1s;
            animation-name: blink;
            animation-iteration-count: infinite;
            animation-direction: alternate;
            animation-timing-function: ease-in-out;
        }
        
        @keyframes blink {
            0% {
                opacity: 1.0;

                color: red;
            }
            50% {
                opacity: 0.0;
            }
            100% {
                opacity: 1.0;
            }
        }
    </style>
<script type="text/javascript">
        var _gaq = _gaq || [];
        _gaq.push(['_setAccount', 'UA-36174445-1']);
        _gaq.push(['_trackPageview']);

        (function() {
            var ga = document.createElement('script');
            ga.type = 'text/javascript';
            ga.async = true;
            ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
            var s = document.getElementsByTagName('script')[0];
            s.parentNode.insertBefore(ga, s);
        })();
    </script>
<script type="text/javascript">
        fishmenu.init({
            mainmenuid: "fish1", //menu DIV id
            orientation: 'h', //Horizontal or vertical menu: Set to "h" or "v"
            classname: 'fishmenu', //class added to menu's outer DIV
            //customtheme: ["#1c5a80", "#18374a"],
            contentsource: "markup" //"markup" or ["container_id", "path_to_menu_file"]
        })
    </script>
<script type="text/javascript">
        var domainroot = "www.cbseacademic.nic.in"

        function Gsitesearch(curobj) {
            curobj.q.value = "site:" + domainroot + " " + curobj.qfront.value
        }
    </script>
<script>
        jQuery(document).on('ready',function($) {

            // hide #back-top first
            $("#back-top").hide();

            // fade in #back-top
            $(function() {
                $(window).scroll(function() {
                    if ($(this).scrollTop() > 100) {
                        $('#back-top').fadeIn();
                    } else {
                        $('#back-top').fadeOut();
                    }
                });

                // scroll body to 0px on click
                $('#back-top a').click(function() {
                    $('body,html').animate({
                        scrollTop: 0
                    }, 800);
                    return false;
                });
            });
        });
    </script>
<style>
        a {
            text-decoration: none;
        }
        /*
Back to top button
*/
        
        #back-top {
            position: fixed;
            bottom: 30px;
            margin-left: -150px;
        }
        
        #back-top a {
            width: 108px;
            display: block;
            text-align: center;
            font: 11px/100% Arial, Helvetica, sans-serif;
            text-transform: uppercase;
            text-decoration: none;
            color: #bbb;
            /* background color transition */
            -webkit-transition: 1s;
            -moz-transition: 1s;
            transition: 1s;
        }
        
        #back-top a:hover {
            color: #000;
        }
        /* arrow icon (span tag) */
        
        #back-top span {
            width: 108px;
            height: 108px;
            display: block;
            margin-bottom: 7px;
            background: #ddd url(up-arrow.png) no-repeat center center;
            /* rounded corners */
            -webkit-border-radius: 15px;
            -moz-border-radius: 15px;
            border-radius: 15px;
            /* background color transition */
            -webkit-transition: 1s;
            -moz-transition: 1s;
            transition: 1s;
        }
        
        #back-top a:hover span {
            background-color: #777;
        }
    </style>
<style>
        .box {
            position: relative;
            background: #FFEBCC;
            border: 1px solid #c0c0c0;
            width: 250px;
            height: 100%;
        }
        
        .box .content {
            padding: 10px;
            position: relative;
        }
        
        .box em {
            display: block;
            width: 11px;
            height: 11px;
            position: absolute;
            /*background: url('resource/images/box-corners.png') no-repeat;*/
            overflow: hidden;
        }
        
        .box em.tl {
            background-position: 0 0;
            left: -1px;
            top: -1px;
        }
        
        .box em.tr {
            background-position: -29px 0;
            right: -1px;
            top: -1px;
        }
        
        .box em.bl {
            background-position: 0 -29px;
            left: -1px;
            bottom: -1px;
        }
        
        .box em.br {

            background-position: -29px -29px;
            right: -1px;
            bottom: -1px;
        }
        
        .clear,
        .box .content {
            display: inline-block;
        }
        
        .clear:after,
        .box .content:after {
            content: ".";
            display: block;
            height: 0;
            clear: both;
            visibility: hidden;
        }
        
        * html .clear,
        * html .box .content {
            height: 1%;
        }
        
        .clear,
        .box .content {
            display: block;
        }
    </style>
<style type="text/css">
        div.ex {
            width: 490;
            padding: 10px;
            border: 5px solid #FF6600;
            margin-left: 10px;
        }
        
        div.ex1 {
            width: 900;
            padding: 10px;
            border: 5px solid #009900;
            margin: 0px;
        }
    </style>
<script language="javascript">
        function blinkFont() {
            //document.getElementById("blink1").style.color="#FF0000";
            //document.getElementById("blink2").style.color="#FF0000";
            //setTimeout("setblinkFont()",480);
        }

        function setblinkFont() {
            //document.getElementById("blink1").style.color="#007A00";
            //document.getElementById("blink2").style.color="#007A00";
            //setTimeout("blinkFont()",480);
        }

        jQuery(document).ready(function($) {
            $("#tshow").click(function() {
                $("#thover").show();
                $("#tpopup").show();
            });
            $("#thover").click(function() {
                $(this).hide();
                $("#tpopup").hide();
            });

