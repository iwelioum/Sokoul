<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @aopyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/

// Compose data MODULE
$orde = zeta_get_option('episodesmodorderby','date');
$ordr = zeta_get_option('episodesmodorder','DESC');
$titl = zeta_get_option('episodestitle','Episodes');
$dspl = zeta_get_option('episodesmdisplay','slider');
$dspl = apply_filters('module_poster_display_ep', $dspl);
$pitm = ($dspl == 'grid') ? zeta_get_option('episodesgitems','14') : zeta_get_option('episodesitems','18');
$auto = zeta_is_true('episodesmodcontrol','autopl');
$slid = zeta_is_true('episodesmodcontrol','slider');
$pmlk = get_post_type_archive_link('episodes');
$totl = zeta_total_count('episodes');
//$class = ($poster == 'vertical') ? 'module-content' : 'hz-module-content';
$styl = zeta_get_option('poster_style_ep', 'horizontal');
$style	= zeta_get_option('poster_style','horizontal');
// Compose Query
$query = array(
    'post_type' => array('episodes'),
    'showposts' => $pitm,
    'orderby'   => $orde,
    'order'     => $ordr
);



// End Data
?>

	<div class="home-module <?php echo $dspl;?> stream episodes" data-module-id="featured-episodes">
	<div class="module-title <?php echo (!$titl) ? 'no-title' : null ;?>">
		<div class="wrapper-box">
			<span><?php echo $titl; ?></span>
			<a href="<?php echo $pmlk; ?>"><?php _z('View All');?></a>
		</div>
	</div>
	<div class="<?php echo ($styl == 'vertical') ? (($style == 'vertical') ? 'module-content' :'vt-module-content') : (($style != 'vertical') ? 'module-content' :'hz-module-content');?>  <?php echo ($dspl == 'slider') ? ' owl-carousel' : null;?>">

	<?php query_posts($query); 
	while(have_posts()){ the_post(); 
	
	
		if($dspl != 'grid' ){
			get_template_part('inc/parts/item_episb'); 
		}else{
			get_template_part('inc/parts/item_epis'); 	
		} 
		
	}
	wp_reset_query(); ?>
	<?php echo ($dspl != 'slider') ? '<div class="clearfix"></div>' : null;?>
	</div>
</div>


