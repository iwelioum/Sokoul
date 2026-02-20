 <?php 
$sidebar = zeta_get_option('sidebar_display');
$blog = zeta_get_option('pageblog');
$scroll = zeta_get_option('sidebar_scroll', 'fixed');
$class = ($scroll == 'scroll') ? 'class="scrolling"' : null;
$home = (isset($args['homep'])) ? $args['homep'] : null;
$single = (isset($args['ptyp'])) ? $args['ptyp'] : null;
$archive = get_queried_object();
$archive = (isset($archive)) ? get_queried_object()->name : null;
$archive = (isset($archive)) ? $archive : null;
$topratings = zeta_get_option('pageratings');
$topimdb = zeta_get_option('pagetopimdb');
$popular = zeta_get_option('pagetrending');

$home = (isset($args['homep'])) ? $args['homep'] : null;

$sidebar = apply_filters('site_sidebar', $sidebar);
if($sidebar == true ){
	
  
  

	echo '<aside '.$class.'>';	
	if($home){
		if(zeta_is_true('sidebar_location','home') === true){
			dynamic_sidebar('sidebar-home');
		}

	}elseif(is_page('blog') || isset($args['blog_arch'])){
		 if(zeta_is_true('sidebar_location','blog_archive') === true){
			dynamic_sidebar('sidebar-blog-archive');
		 }
	}elseif($single == 'blog'){
		if(zeta_is_true('sidebar_location','post') === true){
			dynamic_sidebar('sidebar-blog-single');
		}
	}elseif(is_page() && get_the_ID() == $topratings || get_the_ID() == $topimdb || get_the_ID() == $popular) {
			//zt_widgets_default('archive');
		if(zeta_is_true('sidebar_location','archive') === true){
			dynamic_sidebar('sidebar-archive'); 
        }
	}elseif($archive === 'movies' && zeta_is_true('sidebar_location','archive') === true || $single == 'movies' && zeta_is_true('sidebar_location','movies') === true){		
		if(zeta_is_true('sidebar_location','seasons') === true){
			dynamic_sidebar('sidebar-movies');        
        }        
	}elseif($archive === 'tvshows' && zeta_is_true('sidebar_location','archive') === true || $single == 'tvshows' && zeta_is_true('sidebar_location','tvshows') === true){		
		if(zeta_is_true('sidebar_location','tvshows') === true){
			dynamic_sidebar('sidebar-tvshows');
        }
	}elseif($archive === 'seasons' && zeta_is_true('sidebar_location','archive') === true || $single  == 'seasons' && zeta_is_true('sidebar_location','seasons') === true){		
		if(zeta_is_true('sidebar_location','seasons') === true){
			dynamic_sidebar('sidebar-seasons');	
		}
	}elseif($archive === 'episodes' && zeta_is_true('sidebar_location','archive') === true || $single == 'episodes' && zeta_is_true('sidebar_location','episodes') === true){		
		if(zeta_is_true('sidebar_location','episodes') === true){
			dynamic_sidebar('sidebar-episodes');
		}
	}elseif(empty($archive)){
		
			dynamic_sidebar('sidebar-home');
	}	
	echo '<div class="clearfix"></div>';
	echo '</aside>';

}


?>
