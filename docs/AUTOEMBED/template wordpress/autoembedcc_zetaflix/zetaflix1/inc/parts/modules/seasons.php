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
$orde = zeta_get_option('seasonsmodorderby','date');
$ordr = zeta_get_option('seasonsmodorder','DESC');
$auto = zeta_is_true('seasonsmodcontrol','autopl');
$sldr = zeta_is_true('seasonsmodcontrol','slider');
$dspl = zeta_get_option('seasonsmdisplay','slider');
$dspl = apply_filters('module_poster_display_ss', $dspl);
$styl = zeta_get_option('poster_style_ss', 'vertical');
$pitm = ($dspl == 'grid') ? zeta_get_option('seasonsgitems','18') : zeta_get_option('seasonsitems','18');
$titl = zeta_get_option('seasonstitle','Seasons');
$pmlk = get_post_type_archive_link('seasons');
$totl = zeta_total_count('seasons');
$style	= zeta_get_option('poster_style','horizontal');
// Compose Query
$query = array(
	'post_type' => array('seasons'),
	'showposts' => $pitm,
	'orderby'   => $orde,
	'order'     => $ordr
);

// End Data
?>

	<div class="home-module <?php echo $dspl;?> stream seasons" data-module-id="featured-seasons">
	<div class="module-title <?php echo (!$titl) ? 'no-title' : null ;?>">
		<div class="wrapper-box">
			<span><?php echo $titl; ?></span>
			<a href="<?php echo $pmlk; ?>"><?php _z('View All');?></a>
		</div>
	</div>
	<div class="<?php echo ($styl == 'vertical') ? (($style == 'vertical') ? 'module-content' :'vt-module-content') : (($style != 'vertical') ? 'module-content' :'hz-module-content');?>  <?php echo ($dspl == 'slider') ? ' owl-carousel' : null;?>">
	<?php query_posts($query); while(have_posts()){ the_post(); 
	
	if($dspl == 'grid'){
		get_template_part('inc/parts/item_arch'); 
	}else{
		get_template_part('inc/parts/item'); 
	}
	
	} wp_reset_query(); ?>
	<?php echo ($dspl != 'slider') ? '<div class="clearfix"></div>' : null;?>
	</div>
</div>



