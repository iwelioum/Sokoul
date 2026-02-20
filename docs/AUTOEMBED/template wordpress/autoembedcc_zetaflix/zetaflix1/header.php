<?php 


$style	= zeta_get_option('poster_style','horizontal');

if(is_front_page() || is_home()) {

	$home = true;

}elseif(is_tax()){

	$taxonomy = true;

	$archive = true;

}elseif(is_search()){

	$search = true;

	$archive = true;

}elseif(is_page()){

	$page_post = array(true, get_the_id());

	$page1 = zeta_get_option('pageratings');

	$page2 = zeta_get_option('pagetopimdb');

	$page3 = zeta_get_option('pagetrending');

	if(is_page(array($page1,$page2,$page3))){

		$archive = true;

	}

	$page = true;

}elseif(is_archive()){	

	$ptyp = get_queried_object()->name;

	$page = true;

	switch ($ptyp){

		case 'movies':
		case 'tvshows':

			$archive = true;
			break;

		case 'requests':

			$style = 'vertical';
			break;

		case 'episodes':

			$archive = true;
			$style	= zeta_get_option('poster_style_ep','horizontal');
			break;
			
		case 'seasons':		
			$archive = true;
			$style = zeta_get_option('poster_style_ss', 'vertical');
			break;

	}



}elseif(is_single()){

	$ptyp = get_queried_object()->post_type;

	$single = true;

	$blog_post = (isset($ptyp) == 'post') ? array(true, get_the_id()) : false;	

}

$account = zeta_get_option('pageaccount');


$style2 = zeta_get_option('similar_style','vertical');
$style2 = apply_filters('body_class_style2', $style2);

$style = (isset($single)) ? (($style2 != 'vertical') ? $style : $style2) : $style;


$style = apply_filters('body_class_style', $style);


$rtlon = (is_rtl()) ? true : false;
$homepage = (isset($home) == true) ? 'home' : null;
$loggedin = (is_user_logged_in()) ? 'loggedin' : 'guest';

$search_style = zeta_get_option('search_style', 'default');
$search_bar_style = zeta_get_option('search_bar_style', 'fullw');

$sidebarr = zeta_get_option('sidebar_display');
$width = zeta_get_option('full_width');

$rtl = ($rtlon == true) ? ' rtl-on' : '';
$rtld = ($rtlon == true) ? 'dir="rtl"' : '';

$sidebar_pos = (zeta_get_option('sidebar_position', 'right') == 'left') ? 'sb-l' : 'sb-r';
$sidebar = check_sidebar(isset($sidebarr), isset($home), isset($archive), array(isset($single), isset($ptyp)), isset($page));
$sidebar = apply_filters('body_class_sidebar', $sidebar);
$sliderr = zeta_get_option('slidershow');

$page = (isset($page) == true) ? 'page' : NULL;

$splashscreen = zeta_get_option('splashscreen');
$splash = zeta_get_option('watch_splash');
$playfake = zeta_get_option('playfake');

$watch = '';


$slider = (isset($sliderr) != true) ? 'no-slider' : NULL;

$slider = (isset($home)) ? $slider : NULL;

$body_width = (isset($width) == true) ? 'full-w' : 'fix-w';
$body_width = apply_filters('body_class_width', $body_width);

$single = (isset($single) == true) ? 'single' : NULL;

$archive = (isset($archive) == true || isset($taxonomy) == true || isset($search) == true || is_page()) ? 'archive' : NULL;




$bnme = get_option('blogname');

$blogpage = zeta_get_option('pageblog');

$blog = (get_the_id() === $blogpage || (isset($ptyp) && $ptyp == 'post')) ? 'blog' : null; 

$blogtax = (is_tag() || is_category()) ? 'blogtax' : null; 

$display = zeta_get_option('pageaccount_display');

$searchbar_style = zeta_get_option('search_bar_style', 'fullw');

$fvic = zeta_compose_image_option('favicon');

$logo = zeta_compose_image_option('headlogo');

$logo = ($logo) ? "<img src='{$logo}' alt='{$bnme}'/>" : "<img src='".ZETA_URI."/assets/img/logo.png' alt='{$bnme}'/>";


?>

<!DOCTYPE html>

<html <?php language_attributes(); ?>>

<head>

<meta charset="<?php bloginfo('charset'); ?>" />

<?php if(isset($toic)) echo "<link rel='apple-touch-icon' href='{$toic}'/>\n"; ?>

<meta name="apple-mobile-web-app-capable" content="yes">

<meta name="apple-mobile-web-app-status-bar-style" content="black">

<meta name="mobile-web-app-capable" content="yes">

<meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">

<?php zetaflix_meta_theme_color(isset($styl)); ?>

<?php if($fvic) echo "<link rel='shortcut icon' href='{$fvic}' type='image/x-icon' />\n"; ?>

<?php get_template_part('inc/zeta_seo'); ?>

<?php if($single == true) { zeta_facebook_image("w780", $post->ID); } ?>

<?php wp_head();?>

<?php echo stripslashes(isset($hcod)); ?>

</head>

<body class="<?php echo $sidebar_pos; ?> <?php echo $body_width;?> <?php echo $rtl;?>" <?php echo $rtld;?>>

  <header id="header">

    <div class="topbar">

      <div class="wrapper">

        <div class="tb-left">

          <a class="mobile-control"><i class="fas fa-bars"></i></a>

          <div class="logo">

            <a href="<?php echo apply_filters('site_logo_url', home_url());?>"><?php echo $logo;?></a>

          </div>

		  <?php wp_nav_menu(array('theme_location'=>'header','menu_class'=>'main-menu', 'container'=>false, 'fallback_cb'=>false)); ?>

        </div>

        <div class="tb-right">

          <div class="search-wrap">

            <span class="search-btn"><i class="fas fa-search"></i></span>

          </div>

          <div class="user-wrap">

			<?php zeta_user_menu($account, $loggedin);

			?>

          </div>

        </div>

      </div>

    </div>

    <div class="search-box <?php echo $search_bar_style;?> <?php echo $search_style;?>">

      <form method="get" id="searchform" action="<?php echo esc_url(home_url()); ?>">

        <input type="text" class="main-search" placeholder="<?php _z('Search...');?>" name="s" id="s" value="<?php echo get_search_query(); ?>" autocomplete="off">

		<?php echo ($searchbar_style === 'fullw') ? '<a class="search-close-btn"><i class="fa-solid fa-xmark"></i></a>' : null;?>

      </form>

      <div class="search-results hz"></div>

    </div>



  </header>

<?php 

if(isset($sliderr) && isset($home)){	

		get_template_part('inc/parts/modules/main-slider');

}

?>

<?php echo compose_page_heading(isset($page_post), isset($blog_post));?>



  <div class="main-content <?php echo $sidebar;?> <?php echo $style;?> <?php echo $homepage;?> <?php echo $slider;?> <?php echo $single;?> <?php echo isset($play);?> <?php echo $watch;?> <?php echo $page;?> <?php echo $archive;?> <?php echo $blog;?> <?php echo $blogtax;?>">

    <div class="wrapper">