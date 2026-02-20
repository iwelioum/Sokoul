<?php

/*

* ----------------------------------------------------

* @author: Zetathemes

* @author URI: https://zetathemes.com/

* @copyright: (c) 2023 Zetathemes. All rights reserved

* ----------------------------------------------------

*

* @since 1.0.0

*

*/



class ZetaFlixScripts{



    /**

     * @since 1.0.0

     * @version 1.0

     */

    function __construct(){

        // Include Scripts / CSS files



		//add_action('admin_head',array($this,'extra_admin_scripts'));

        add_action('admin_enqueue_scripts', array($this,'admin_scripts'), 20);

        add_action('wp_enqueue_scripts',array($this,'front_scripts'));

        // Include content on Footer and Header

        add_action('wp_head', array($this,'header_scripts'));

        add_action('wp_footer', array($this,'footer_scripts'));

    }



    /**

     * @since 1.0.0

     * @version 1.0

     */

    public function header_scripts(){
		
		$adminbar = zeta_is_true('permits','sab');

        if(is_page() OR is_single()){

            $zetacmts = zeta_get_option('comments');

            switch ($zetacmts) {

                case 'fb':

                    $appi = zeta_get_option('fbappid');

                    $lang = zeta_get_option('fblang','en_US');

                    require_once(ZETA_DIR.'/inc/parts/jscomments_facebook.php');

                    break;



                case 'dq':

                    $sname = zeta_get_option('dqshortname');

                    if($sname){

                        require_once(ZETA_DIR.'/inc/parts/jscomments_disqus.php');

                    }

                    break;

            }

        }

        echo "<script type=\"text/javascript\">jQuery(document).ready(function(a){\"false\"==ztGonza.mobile&&a(window).load(function(){a(\".scrolling\").mCustomScrollbar({theme:\"minimal-dark\",scrollInertia:200,scrollButtons:{enable:!0},callbacks:{onTotalScrollOffset:100,alwaysTriggerOffsets:!1}})})});</script>";
		
		
		echo ($adminbar && (current_user_can('editor') || current_user_can('administrator'))) ? "<style type=\"text/css\">header { top: 32px; } @media screen and (max-width: 782px) { header { top: 46px; } } @media screen and (max-width: 600px){ #wpadminbar { position: fixed; } } </style>" : null;

    }



    /**

     * @since 1.0.0

     * @version 1.0

     */

    public function footer_scripts(){

        #globals

		global $user_ID, $post;

		# Options

		$zt_slider_layout		= zeta_get_option('sliderlayout');

		$zt_slider_ap			= zeta_is_true('slidercontrols','autoplay');

		$zt_slider_hp			= zeta_is_true('slidercontrols','hoverpause');

		$zt_slider_id			= zeta_is_true('slidercontrols','indicator');

		$zt_sidebar				= zeta_get_option('sidebar_display');
		
		$zt_sidebar				= apply_filters('script_sidebar', $zt_sidebar);

		$zt_sidebarloc			= zeta_get_option('sidebar_location');

		$zt_poster_style		= zeta_get_option('poster_style');

		$zt_slider_speed		= zeta_get_option('sliderspeed','4000');

		$zt_google_analytics	= zeta_get_option('ganalytics');

	    $zt_full_width			= zeta_get_option('full_width');
		
		$zt_full_width			= apply_filters('script_fullwidth', $zt_full_width);

		$zt_splash_click		= zeta_get_option('splashscreen_click');

		$zt_player_ajax 		= zeta_get_option('playajax');

		$zt_player_autoplay 	= zeta_get_option('playautoload');
		
		$zt_rtl					= (is_rtl()) ? true : false;
		
		$zt_rtl					= apply_filters('script_rtl', $zt_rtl);		

		$zt_rtl_carousel 		= ($zt_rtl == true) ? "rtl:true," : null;		
		
		$zt_rtl_carousel		= apply_filters('script_rtl_carousel', $zt_rtl_carousel);

		$account 				= zeta_get_option('pageaccount');

		$account_pages 			= zeta_get_option('pageaccount_display','single');

		

		$list 					= zeta_get_option('pageaccount_list', 'my-list');

		$seen 					= zeta_get_option('pageaccount_seen', 'seen');

		$links 					= zeta_get_option('pageaccount_links', 'links');

		$linkspending 			= zeta_get_option('pageaccount_linkspending', 'links-pending');

		$settings 				= zeta_get_option('pageaccount_settings', 'settings');		

		

		//Pages 

		$zt_account 			= zeta_get_option('pageaccount');		

		$poster 				= zeta_get_option('poster_style','horizontal');
		$poster					= apply_filters('script_poster_style', $poster);

		$poster_r 				= zeta_get_option('similar_poster','vertical');
		


		

		# conditionals		

		$cond_00 = 'true';

		$cond_01 = 'false';

		$cond_02 = 'false';

		if($zt_slider_layout != 'fulls'){

			

			$cond_00 = ($zt_slider_ap == true) ? 'true' : 'false';

			$cond_01 = ($zt_slider_hp == true) ? 'true' : 'false';

			$cond_02 = ($zt_slider_id == true) ? 'true' : 'false';			

			

		}





        # HTML Out

        $out_javascript = "<script type=\"text/javascript\">\n";		



		//PAGE READY

		$out_javascript .= " jQuery(document).ready(function($) {\n";

		

		

		

		

		$out_javascript .= "
		function checkClasses(sliderClass){
			 sliderClass.each(function() {";
				$out_javascript .= "var total = $(this).find('.owl-item.active').length;";
		if($zt_rtl == true){
			$out_javascript .= "var totalItems = total - 1;";
		}else{
			$out_javascript .= "var totalItems = total;";
		}
		$out_javascript .= "$(this).find('.owl-item').removeClass('firstActiveItem'); 
				 			$(this).find('.owl-item').removeClass('lastActiveItem'); 
							$(this).find('.owl-item.active').each(function(index) { 
								if (index === 0) { 
									$(this).addClass('firstActiveItem') 
								} 
								if (index === totalItems - 1 && totalItems > 1) { 
									$(this).addClass('lastActiveItem') 
								} 
							}) 
			}) 
		}";	

		

		

		if(is_page($account) || is_author()){

			if($account_pages !== "multi" || is_author()){

				//Account page auto select

				if(isset($_GET[$settings])){

				$out_javascript .="$(\".profile-tab-menu li#acc-sett a\").addClass('active'); $(\".profile-tab-page div#settings\").addClass('active'); "; 

				}elseif(isset($_GET[$links]) ||	 isset($_GET[$linkspending])){

					$out_javascript .="$(\".profile-tab-menu li#acc-link a\").addClass('active'); $(\".profile-tab-page div#links\").addClass('active'); $(\".profile-tab-page .tab-page-title .pending\").show(); "; 

				}elseif(isset($_GET[$seen])){

					$out_javascript .="$(\".profile-tab-menu li#acc-seen a\").addClass('active'); $(\".profile-tab-page div#seen\").addClass('active'); "; 

				}elseif(isset($_GET[$list])){

					$out_javascript .="$(\".profile-tab-menu li#acc-list a\").addClass('active'); $(\".profile-tab-page div#list\").addClass('active'); "; 

				}else{

					$out_javascript .="$(\".profile-tab-menu li:first-child a\").addClass('active'); $(\".profile-tab-page div:nth-child(3)\").addClass('active');";

				}

			}

			

			

			$out_javascript .= "$(\".settings-menu li a\").click(function(){ var selectedTab = $(this).data(\"profilesett\"); var openTab = \".settings-tab.\" + selectedTab; $(\".settings-menu li a\").removeClass(\"active\"); $(\".settings-tab\").removeClass(\"active\"); $(openTab).addClass(\"active\"); $(this).addClass(\"active\"); });";

			

		}

		

		

		if($account_pages == "multi" && is_page($settings)){

			$out_javascript .= "$(\".settings-menu li a\").click(function(){ var selectedTab = $(this).data(\"profilesett\"); var openTab = \".settings-tab.\" + selectedTab; $(\".settings-menu li a\").removeClass(\"active\"); $(\".settings-tab\").removeClass(\"active\"); $(openTab).addClass(\"active\"); $(this).addClass(\"active\"); });";

		}			





		if($zt_google_analytics) {

			$out_javascript .= "(function(b,c,d,e,f,h,j){b.GoogleAnalyticsObject=f,b[f]=b[f]||function(){(b[f].q=b[f].q||[]).push(arguments)},b[f].l=1*new Date,h=c.createElement(d),j=c.getElementsByTagName(d)[0],h.async=1,h.src=e,j.parentNode.insertBefore(h,j)})(window,document,\"script\",\"//www.google-analytics.com/analytics.js\",\"ga\"),ga(\"create\",\"{$zt_google_analytics}\",\"auto\"),ga(\"send\",\"pageview\");\n";

		}

		

		

		

		

		if(is_home() || is_front_page()){

			

			// MULTI FEATURED - TAB PAGE FIRST CHILD

			$out_javascript .= "$(\"#featured-multi div:nth-child(2)\").addClass('active');";

			// MULTI FEATURED - TAB BUTTON FIRST CHILD

			$out_javascript .= "$(\".module-tabs li:nth-child(2) a\").addClass('active');";

			

			$out_javascript .= "$(\".module-tabs li a\").click(function(){ var lnk = $(this).data(\"tab-urlid\"); var id = $(this).closest(\".home-module\").attr(\"id\"); var id2 =  $(this).data(\"multi-tabid\"); var module = \"#\" + id; $('.module-link').attr('href', '".site_url()."/' + lnk + '/'); $(\".module-tabs li a\").removeClass(\"active\"); $(module + \" .module-content\").removeClass(\"active\"); $(module + \" .module-content.\" + id2).addClass(\"active\"); $(this).addClass(\"active\"); });";

			

			

			

			

			// MAIN SLIDER (HOME)

			$out_javascript .= "$(\"#main-slider\").owlCarousel({ ".$zt_rtl_carousel." autoplay: ".$zt_slider_speed .", autoplayHoverPause: ".$cond_01.", autoHeight: false, autoplayTimeout: ".$zt_slider_speed.", dots: ".$cond_02.", pagination: false, slideSpeed: 300, paginationSpeed: 400, loop: true, items: 1, itemsDesktop: false, itemsDesktopSmall: false, itemsTablet: false, itemsMobile: false, animateOut: 'fadeOut', animateIn: 'fadeIn', stagePadding: 0});";

			

			// VERTICAL SLIDER (HOME)

			if($poster == "vertical"){

				if($zt_full_width == true){

					if($zt_sidebar == true && zeta_is_true('sidebar_location', 'home')){

						$items = "0:{ items:2, slideBy: 2 }, 400:{  items:3, slideBy: 3 }, 550:{  items:4, slideBy: 4 }, 800:{  items:5, slideBy: 5 }, 1000:{items:4, slideBy:4}, 1150:{items:5,slideBy:5}, 1400:{ items:6, slideBy: 6 }, 1700:{ items:7, slideBy: 7 }, 1900:{ items:8, slideBy: 8 }, 2300:{ items:9, slideBy: 9}";

					}else{

						$items = "0:{ items:2, slideBy: 2 }, 400:{  items:3, slideBy: 3 }, 650:{  items:4, slideBy: 4 }, 900:{  items:5, slideBy: 5 }, 1100:{ items:6, slideBy: 6 }, 1400:{ items:7, slideBy: 7 }, 1800:{ items:8, slideBy: 8 }, 2300:{ items:9, slideBy: 9}";

					}

				}else{

					if($zt_sidebar == true && zeta_is_true('sidebar_location', 'home')){

						$items = "0:{ items:2, slideBy: 2 }, 400:{ items:3, slideBy: 3 }, 600:{ items:4, slideBy: 4 }, 800:{  items:5, slideBy: 5 }, 1000:{ items:4, slideBy: 4 }, 1100:{ items:5, slideBy: 5 }, 1400:{ items:6, slideBy: 6 }";

					}else{

						$items = "0:{ items:2, slideBy: 2 }, 400:{ items:3, slideBy: 3 }, 600:{ items:4, slideBy: 4 }, 800:{ items:5, slideBy: 5 }, 1100:{ items:6, slideBy: 6 }, 1300:{ items:7, slideBy: 7 }, 1600:{ items:8, slideBy: 8 }";

					}

				}



			}else{

			// HORIZONTAL SLIDER (HOME)

				if($zt_full_width == true){

					if($zt_sidebar == true && zeta_is_true('sidebar_location', 'home')){

						$items = "0:{ items:2, slideBy: 2 }, 600:{  items:3, slideBy: 3 }, 800:{  items:4, slideBy: 4 }, 1000:{ items: 3, slideBy: 3 }, 1250:{ items: 4, slideBy: 4 }, 1400:{  items:5, slideBy: 5 }, 1700:{  items:6, slideBy: 6 }, 2100:{  items:7, slideBy:7 },";						

					}else{

						$items = "0:{ items:2, slideBy: 2 }, 600:{  items:3, slideBy: 3 }, 800:{  items:4, slideBy: 4 }, 1100:{  items:5, slideBy: 5 }, 1400:{  items:6, slideBy: 6 }, 1800:{  items:7, slideBy: 7 },";

					}

				}else{

					if($zt_sidebar == true && zeta_is_true('sidebar_location', 'home')){

						$items = "0:{ items:2, slideBy: 2 }, 600:{ items:3, slideBy: 3 }, 800:{ items:4, slideBy: 4 }, 1000:{ items:3, slideBy: 3}, 1250:{ items:4, slideBy: 4}, 1400:{ items:5, slideBy: 5 }";

					}else{

						//$items = "0:{ items:2, slideBy: 2 }, 500:{ items:3, slideBy: 3 }, 800:{ items:4, slideBy: 4 }, 1150:{ items:5, slideBy: 5 }, 1350:{ items:6, slideBy: 6 }";

						$items = "0:{ items:2, slideBy: 2 }, 600:{  items:3, slideBy: 3 }, 800:{  items:4, slideBy: 4 }, 1100:{  items:5, slideBy: 5 }, 1400:{  items:6, slideBy: 6 }";

					}

					

				}

			}

			

				$out_javascript .= "var moduleSlider = $(\".home-module.slider .module-content\"); moduleSlider.owlCarousel({  ".$zt_rtl_carousel." smartSpeed: 90,  autoHeight: false, autoplay: false, autoplayHoverPause: true, dots: false, pagination: false, nav: true, slideSpeed: 100, paginationSpeed: 400, loop: true, items: 2, slideBy: 2, margin: 10, autoHeight: false, stagePadding:0, responsive:{ ".$items." } });";			

				

			

			// EPISODE MODULE HOME	

				if($zt_full_width == true){

					if($zt_sidebar == true && zeta_is_true('sidebar_location', 'home')){

						$itemse = "0:{ items:2, slideBy: 2 }, 600:{  items:3, slideBy: 3 }, 800:{  items:4, slideBy: 4 }, 1000:{ items: 3, slideBy: 3 }, 1250:{ items: 4, slideBy: 4 }, 1400:{  items:5, slideBy: 5 }, 1700:{  items:6, slideBy: 6 }, 2100:{  items:7, slideBy:7 },";						

					}else{

						$itemse = "0:{ items:2, slideBy: 2 }, 600:{  items:3, slideBy: 3 }, 600:{  items:3, slideBy: 3 }, 800:{  items:4, slideBy: 4 }, 1100:{  items:5, slideBy: 5 }, 1400:{  items:6, slideBy: 6 }, 1800:{  items:7, slideBy: 7 },";

					}

				}else{

					if($zt_sidebar == true && zeta_is_true('sidebar_location', 'home')){

						$itemse = "0:{ items:2, slideBy: 2 }, 600:{ items:3, slideBy: 3 }, 800:{ items:4, slideBy: 4 }, 1000:{ items:3, slideBy: 3}, 1250:{ items:4, slideBy: 4}, 1400:{ items:5, slideBy: 5 }";

					}else{

						//$items = "0:{ items:2, slideBy: 2 }, 500:{ items:3, slideBy: 3 }, 800:{ items:4, slideBy: 4 }, 1150:{ items:5, slideBy: 5 }, 1350:{ items:6, slideBy: 6 }";

						$itemse = "0:{ items:2, slideBy: 2 }, 600:{  items:3, slideBy: 3 }, 600:{  items:3, slideBy: 3 }, 800:{  items:4, slideBy: 4 }, 1100:{  items:5, slideBy: 5 }, 1400:{  items:6, slideBy: 6 }";

					}

				}



				$out_javascript .= " var moduleSliderhz = $(\".home-module.slider .hz-module-content\"); moduleSliderhz.owlCarousel({  ".$zt_rtl_carousel." smartSpeed: 90,  autoHeight: false, autoplay: false, autoplayHoverPause: true, dots: false, pagination: false, nav: true, slideSpeed: 100, paginationSpeed: 400, loop: true, items: 2, slideBy: 2, margin: 10, autoHeight: false, stagePadding:0, responsive:{ ".$itemse." } });";



				if($zt_full_width == true){

					if($zt_sidebar == true && zeta_is_true('sidebar_location', 'home')){

						$itemsvt = "0:{ items:2, slideBy: 2 }, 400:{  items:3, slideBy: 3 }, 550:{  items:4, slideBy: 4 }, 800:{  items:5, slideBy: 5 }, 1000:{items:4, slideBy:4}, 1150:{items:5,slideBy:5}, 1400:{ items:6, slideBy: 6 }, 1700:{ items:7, slideBy: 7 }, 1900:{ items:8, slideBy: 8 }, 2300:{ items:9, slideBy: 9}";

					}else{

						$itemsvt = "0:{ items:2, slideBy: 2 }, 400:{  items:3, slideBy: 3 }, 650:{  items:4, slideBy: 4 }, 900:{  items:5, slideBy: 5 }, 1100:{ items:6, slideBy: 6 }, 1400:{ items:7, slideBy: 7 }, 1800:{ items:8, slideBy: 8 }, 2300:{ items:9, slideBy: 9}";

					}

				}else{

					if($zt_sidebar == true && zeta_is_true('sidebar_location', 'home')){

						$itemsvt = "0:{ items:2, slideBy: 2 }, 400:{ items:3, slideBy: 3 }, 600:{ items:4, slideBy: 4 }, 800:{  items:5, slideBy: 5 }, 1000:{ items:4, slideBy: 4 }, 1100:{ items:5, slideBy: 5 }, 1400:{ items:6, slideBy: 6 }";

					}else{

						$itemsvt = "0:{ items:2, slideBy: 2 }, 400:{ items:3, slideBy: 3 }, 600:{ items:4, slideBy: 4 }, 800:{ items:5, slideBy: 5 }, 1100:{ items:6, slideBy: 6 }, 1300:{ items:7, slideBy: 7 }, 1600:{ items:8, slideBy: 8 }";

					}

				}



				// Override Vertical Sliders
				$out_javascript .= "var moduleSlidervt = $(\".home-module.slider .vt-module-content\"); moduleSlidervt.owlCarousel({  ".$zt_rtl_carousel." smartSpeed: 90,  autoHeight: false, autoplay: false, autoplayHoverPause: true, dots: false, pagination: false, nav: true, slideSpeed: 100, paginationSpeed: 400, loop: true, items: 2, slideBy: 2, margin: 10, autoHeight: false, stagePadding:0, responsive:{ ".$itemsvt." } });";			
				
				
			

			
			
			

			

			//SLIDER LEFT & RIGHT FIX			

			$out_javascript .= "checkClasses(moduleSlider); checkClasses(moduleSliderhz); checkClasses(moduleSlidervt); moduleSlider.on('resized.owl.carousel', function(event) { checkClasses(moduleSlider); }); moduleSlider.on('translated.owl.carousel', function(event) { checkClasses(moduleSlider); });  moduleSliderhz.on('resized.owl.carousel', function(event) { checkClasses(moduleSliderhz); }); moduleSliderhz.on('translated.owl.carousel', function(event) { checkClasses(moduleSliderhz); }); moduleSlidervt.on('resized.owl.carousel', function(event) { checkClasses(moduleSlidervt); });  moduleSlidervt.on('translated.owl.carousel', function(event) { checkClasses(moduleSlidervt); });";

			

			

			



		}



		

		

		if(  is_singular( array( 'movies', 'tvshows', 'seasons', 'episodes' ) )  ){

			

			

			

			// GALLERY SINGLE

			$out_javascript .= "$(\".content-gall\").owlCarousel({  ".$zt_rtl_carousel." autoplay: false,autoplayHoverPause: true,dots: true,pagination: true,slideSpeed: 300,paginationSpeed: 400,loop: false,items: 6,itemsDesktop: false,itemsDesktopSmall: false,itemsTablet: false,itemsMobile: false,animateOut: 'fadeOut',animateIn: 'fadeIn', responsive:{ 0:{ items:2, slideBy: 2 }, 350:{ items:2, slideBy: 2 }, 500:{ items:3, slideBy: 3 }, 600:{ items:4, slideBy: 4 }, 900:{ items:4, slideBy: 4 }, 1000:{ items:4, slideBy: 4 }, 1100:{ items:4, slideBy: 4 }, 1300:{ items:5, slideBy: 5 }, 1700:{ items:6, slideBy: 6 }, 2000:{ items:7, slideBy: 7 } }});";

			

			

			// SIMILAR HOVER SHOW

			$out_javascript .= "$(\".module-content.owl-carousel\").hover(function(){ $(this).toggleClass(\"hover\"); }); $(\".module-content.owl-carousel .owl-item\").hover(function(){ $(this).toggleClass(\"hover\"); });";

			

			

			if($poster == "vertical"){

				if($zt_full_width == true){

					if($zt_sidebar == true && zeta_is_true('sidebar_location', 'home')){

						$itemsr = "0:{ items:2, slideBy: 2 }, 450:{  items:3, slideBy: 3 }, 650:{  items:4, slideBy: 4 }, 850:{  items:5, slideBy: 5 }, 1000:{items:4, slideBy:4}, 1150:{items:5,slideBy:5}, 1450:{ items:6, slideBy: 6 }, 1700:{ items:7, slideBy: 7 }, 1900:{ items:8, slideBy: 8 }, 2300:{ items:9, slideBy: 9}";

					}else{

						$itemsr = "0:{ items:2, slideBy: 2 }, 450:{  items:3, slideBy: 3 }, 650:{  items:4, slideBy: 4 }, 850:{  items:5, slideBy: 5 }, 1100:{ items:6, slideBy: 6 }, 1400:{ items:7, slideBy: 7 }, 1800:{ items:8, slideBy: 8 }, 2300:{ items:9, slideBy: 9}";

					}

				}else{

					if($zt_sidebar == true && zeta_is_true('sidebar_location', 'home')){

						$itemsr = "0:{ items:2, slideBy: 2 }, 450:{ items:3, slideBy: 3 }, 650:{ items:4, slideBy: 4 }, 850:{  items:5, slideBy: 5 }, 1000:{ items:4, slideBy: 4 }, 1150:{ items:5, slideBy: 5 }, 1400:{ items:6, slideBy: 6 }";

					}else{

						$itemsr = "0:{ items:2, slideBy: 2 }, 450:{ items:3, slideBy: 3 }, 650:{ items:4, slideBy: 4 }, 850:{ items:5, slideBy: 5 }, 1100:{ items:6, slideBy: 6 }, 1300:{ items:7, slideBy: 7 }, 1600:{ items:8, slideBy: 8 }";

					}

				}



			}else{

			// HORIZONTAL SLIDER (HOME)

				if($zt_full_width == true){

					if($zt_sidebar == true && zeta_is_true('sidebar_location', 'home')){

						$itemsr = "0:{ items:2, slideBy: 2 }, 650:{  items:3, slideBy: 3 }, 900:{  items:4, slideBy: 4 }, 1000:{ items: 3, slideBy: 3 }, 1250:{ items: 4, slideBy: 4 }, 1600:{  items:5, slideBy: 5 }, 1900:{  items:6, slideBy: 6 }, 2200:{  items:7, slideBy:7 },";						

					}else{

						$itemsr = "0:{ items:2, slideBy: 2 }, 700:{  items:3, slideBy: 3 }, 1000:{  items:4, slideBy: 4 }, 1300:{  items:5, slideBy: 5 }, 1600:{  items:6, slideBy: 6 }, 1900:{  items:7, slideBy: 7 },";

					}

				}else{

					if($zt_sidebar == true && zeta_is_true('sidebar_location', 'home')){

						$itemsr = "0:{ items:2, slideBy: 2 }, 600:{ items:3, slideBy: 3 }, 800:{ items:4, slideBy: 4 }, 1000:{ items:3, slideBy: 3}, 1250:{ items:4, slideBy: 4}, 1400:{ items:5, slideBy: 5 }";

					}else{

						$itemsr = "0:{ items:2, slideBy: 2 }, 600:{  items:3, slideBy: 3 }, 800:{  items:4, slideBy: 4 }, 1100:{  items:5, slideBy: 5 }, 1400:{  items:6, slideBy: 6 }";

					}

					

				}



			}

			

			

			$out_javascript .= "var moduleSlider = $(\".similar-module .module-content\");moduleSlider.owlCarousel({ ".$zt_rtl_carousel."  rewind: true, navRewind:true, smartSpeed: 90,autoplay: false,autoplayHoverPause: true,dots: false,pagination: false,nav: true,slideSpeed: 100,paginationSpeed: 400,loop: true,items: 7,slideBy: 7,margin: 10,stagePadding:0,responsive:{".$itemsr."}});";

			

			$out_javascript .= "checkClasses(moduleSlider); moduleSlider.on('resized.owl.carousel', function(event) { checkClasses(moduleSlider); }); moduleSlider.on('translated.owl.carousel', function(event) { checkClasses(moduleSlider); });";			

			

            

			

			if(is_singular(array('tvshows','seasons', 'episodes'))){						

				

				$season_var = get_query_var('season');

				$season_var = explode('/',$season_var);

				$ss_val = isset($season_var[0]) ? (int)$season_var[0] : null;

				$ep_var = isset($season_var[1]) ? $season_var[1] : null;

				$ep_val = isset($season_var[2]) ? (int)$season_var[2] : null;

				$watch_var = isset($season_var[3]) ? $season_var[3] : null;

				$watch_val = isset($season_var[4]) ? (int)$season_var[4] : null;

			

				if(empty($ss_val) || $post->post_type === 'episodes'){

					$out_javascript .= "$('.season-select ul:first-child').addClass('active');";

				}

			

			}

		

			$out_javascript .= "window.onload = function() {";

			

			if(is_singular(array('movies', 'episodes'))):
			if($zt_player_ajax && $zt_player_autoplay){

				$out_javascript .= "document.getElementById(\"playeroptionsul\").firstElementChild.click();";

			}else{

				$out_javascript .= "document.getElementById(\"splash-play\").addEventListener(\"click\", function() {";

				if($zt_splash_click == 'vid'){

					$out_javascript .= "if ( $(\"#player-option-1\").length ) { document.getElementById(\"player-option-1\").click(); } else { document.getElementById(\"playeroptionsul\").firstElementChild.click(); }";

				}else{

					$out_javascript .= "document.getElementById(\"playeroptionsul\").firstElementChild.click();";

				}

				$out_javascript .= "});";

			}

			endif;

			$out_javascript .= "}";

			

		}	



		

		$out_javascript .= "});";

		$out_javascript .="</script>\n";

		

		// Out

		echo apply_filters('zetaflix_front_footer', $out_javascript);

    }



    /**

     * @since 1.0.0

     * @version 1.0

     */

    public function front_scripts(){

        // Set Font Awesome
		
		$playajaxep = zeta_get_option('playajaxep');
		$autoloadajaxep = zeta_get_option('playautoloadep');
		$autoload_ajaxep = ($playajaxep == true && $autoloadajaxep == true) ? true : false;
		$fontawesome = zeta_get_option('fontawesome_mode','local');
		$adminbar = zeta_is_true('permits', 'sab');

		if(!$adminbar){
			wp_deregister_style('dashicons');
			wp_dequeue_style( 'wp-block-library' );	
		}
        

        wp_enqueue_style('fontawesome-pro', ZETA_URI.'/assets/css/all.min.css', array(), '6.11.1');

		wp_enqueue_style('scrollbar', ZETA_URI.'/assets/css/front.scrollbar'.zeta_devmode().'.css', array(), ZETA_VERSION);					

		wp_enqueue_style('streamflix-carousel', ZETA_URI.'/assets/css/owl.carousel.min.css', array(), ZETA_VERSION);

		wp_enqueue_style('streamflix-style', ZETA_URI.'/assets/css/main.css', array(), ZETA_VERSION);		

		wp_enqueue_script('owl-carousel',ZETA_URI.'/assets/js/owl.carousel.min.js', array('jquery'), ZETA_VERSION, false);

        wp_enqueue_script('scrollbar',ZETA_URI.'/assets/js/lib/scrollbar.js', array('jquery'), ZETA_VERSION, false);

		if(is_singular(array('movies','tvshows', 'seasons', 'episodes'))){

			wp_enqueue_script('ztRepeat', ZETA_URI.'/assets/js/lib/isrepeater.js', array('jquery'), ZETA_VERSION, false);

		}		

        // Front JavaScripts

        wp_enqueue_script('scripts', ZETA_URI.'/assets/js/front.scripts'.zeta_devmode().'.js', array('jquery'), ZETA_VERSION, true);

        wp_enqueue_script('zt_main_ajax', ZETA_URI.'/assets/js/front.ajax'.zeta_devmode().'.js', array('jquery'), ZETA_VERSION, false);

        wp_enqueue_script('live_search', ZETA_URI.'/assets/js/front.livesearch'.zeta_devmode().'.js', array('jquery'), ZETA_VERSION, true);

        wp_localize_script('zt_main_ajax', 'ztAjax', array(

            'url'		  => admin_url('admin-ajax.php', 'relative'),

            'player_api'  => site_url('/wp-json/zetaplayer/v2/'),

            'play_ajaxmd' => zeta_get_option('playajax'),

            'play_method' => zeta_get_option('playajaxmethod','admin_ajax'),

			'play_method_ep' => zeta_get_option('playajaxmethodep','admin_ajax'),

			'ajaxep_error'	=> __z('Select an episode to watch'),

            'googlercptc' => zeta_get_option('gcaptchasitekeyv3'),

            'classitem'   => (zeta_get_option('max_width','1200') >= 1400 ) ? 6 : 5,

            'loading'	  => __z('Loading..'),

            'alist'  => __z('Add to List'),

            'rlist'  => __z('Remove of List'),

            'aseen'  => __z('Mark as Seen'),

            'rseen'  => __z('Remove of Seen'),

			'guest'  => __z('Sign In'),

            'views'     => __z('Views'),

            'remove'	=> __z('Remove'),

            'isawit'	=> __z('I saw it'),

            'send'		=> __z('Data send..'),

            'updating'	=> __z('Updating data..'),

            'error'		=> __z('Error'),

			'error_ajaxep' => __z('Select an episode to watch'),

            'pending'	=> __z('Pending review'),

            'ltipe'		=> __z('Download'),

            'sending'	=> __z('Sending data'),

            'enabled'	=> __z('Enable'),

            'disabled'	=> __z('Disable'),

            'trash'		=> __z('Delete'),

            'lshared'	=> __z('Links Shared'),

            'ladmin'	=> __z('Manage pending links'),

            'sendingrep'=> __z('Processing report..'),

            'ready'		=> __z('Ready'),

			'request' 	=> __z('Request'),

			'reqsent' 	=> __z('Request sent'),

			'linksent' => __z('Data sent successfully.'),

			'linkerror' => __z('Data sent successfully.'),

            'deletelin' => __z('Do you really want to delete this link?'),

			'links_row'	=> __z('Add Row'),

			'links_add'	=> __z('Add Links'),
			
			'atld_ajaxep' => $autoload_ajaxep,

        ));

        wp_localize_script('live_search', 'ztGonza', array(

			'api'	           => zetaflix_url_search(),

	        'glossary'         => zetaflix_url_glossary(),

			'nonce'            => zetaflix_create_nonce('zetaflix-search-nonce'),

			'area'	           => ".search-results",

			'button'	       => ".search-button",

			'more'		       => __z('View all results'),

			'mobile'	       => zeta_mobile(),

			'reset_all'        => __z('Really you want to restart all data?'),

			'manually_content' => __z('They sure have added content manually?'),

	        'loading'          => __z('Loading..'),

            'loadingplayer'    => __z('Loading player..'),

            'selectaplayer'    => __z('Select a video player'),

            'playeradstime'    => zeta_get_option('playwait'),

            'autoplayer'       => zeta_get_option('playautoload'),

            'livesearchactive' => zeta_is_true('permits','enls'),

			'btn'			   => __z('Watch Now'),

		));

        // Comments // gallery

        if(is_singular() && get_option('thread_comments')) {

			wp_enqueue_script('comment-reply');

		}

    }



    /**

     * @since 1.0.0

     * @version 1.0

     */

    public function admin_scripts(){

		

        // Admin CSS

    	wp_enqueue_style('admin_css', ZETA_URI.'/assets/css/admin.style'.zeta_devmode().'.css', false, ZETA_VERSION);

        // Admin Javascript

		

		if ( ! did_action( 'wp_enqueue_media' ) ) {

			wp_enqueue_media();

		}

		wp_enqueue_script( 'wpmedia', get_stylesheet_directory_uri() . '/assets/js/lib/wpmedia.js', array('jquery'), null, false );		

		wp_enqueue_script( 'wpmultimedia', get_stylesheet_directory_uri() . '/assets/js/lib/wpmultimedia.js', array('jquery'), null, false );		

        wp_enqueue_script('ajax_zetaflix_upload', ZETA_URI.'/assets/js/lib/wpupload.js', array('jquery'), ZETA_VERSION, false);

        wp_enqueue_script('ajax_zetaflix_admin', ZETA_URI.'/assets/js/admin.ajax'.zeta_devmode().'.js', array('jquery'), ZETA_VERSION, false);

		wp_localize_script('ajax_zetaflix_admin', 'zetaAj', array(

			'adminurl'			 => admin_url(),

			'url'                => admin_url('admin-ajax.php', 'relative'),

            'rem_featu'	         => __('Remove'),

			'add_featu'          => __('Add'),

			'loading'	         => __z('Loading...'),

			'reloading'          => __z('Reloading..'),

			'mtupdbulk'			 => __z('Bulk Update'),

			'mtupdcompl'		 => __z('Processsed'),

			'exists'	         => __z('Domain has already been registered'),

			'updb'		         => __z('Updating database..'),

			'completed'          => __z('Action completed'),

            'nolink'             => __z('The links field is empty'),

            'deletelink'         => __z('Do you really want to delete this item?'),

            'confirmdbtool'      => __z('Do you really want to delete this register, once completed this action will not recover the data again?'),

            'confirmpublink'     => __z('Do you want to publish the links before continuing?'),

			'domain'	         => zeta_compose_domain( get_site_url() ),

			'zetathemes_server'	 => 'https://cdn.bescraper.cf/api',

			'zetathemes_license'  => (current_user_can('administrator')) ? get_option(ZETA_THEME_SLUG. '_license_key') : '',

			'zetathemes_item'	 => ZETA_THEME,

		));

    }

	

	public function extra_admin_scripts(){



		echo '<style>';

		echo ' #major-publishing-actions { display: block!important; opacity: 1!important; visibility: visible!important; } #misc-publishing-actions #major-publishing-actions.submitcheck { display: none!important; }';

		echo '</style>';

	}

}



// Zetaflix Front/Admin Scripts

new ZetaFlixScripts;

