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

// All Postmeta
$classlinks = new ZetaLinks;

$postmeta = zeta_postmeta_movies($post->ID);
$adsingle = zeta_compose_ad('_zetaflix_adsingle');
// Movies Meta data
$runtime = zeta_isset($postmeta,'runtime');
$imdbr = get_post_meta($post->ID, 'imdbRating', true);
$imdbv = get_post_meta($post->ID, 'imdbVotes', true);
$trailer = zeta_isset($postmeta,'youtube_id');
$pviews  = zeta_isset($postmeta,'zt_views_count');
$player  = zeta_isset($postmeta,'players');
$player  = maybe_unserialize($player);
$images  = zeta_isset($postmeta,'imagenes');
$tviews  = ($pviews) ? sprintf( __z('%s Views'), $pviews) : __z('0 Views');


$keywords = get_the_term_list($post->ID, 'ztkeywords');
$quality = get_the_term_list( $post->ID, 'ztquality');

$backdrop = omegadb_get_poster('', $post->ID, '', 'zt_backdrop', 'original');
$poster = omegadb_get_poster('', $post->ID);

$pageplay = zeta_get_option('pagewatchplay');
$pageplay = get_permalink($pageplay);

$watch = get_query_var('watch');
$watch_location = zeta_get_option('watch_location', 'same');

//  Image
$dynamicbg  = omegadb_get_rand_image($images);
// Options
$player_ads = zeta_compose_ad('_zetaflix_adplayer');
$player_wht = zeta_get_option('playsize','regular');
$imdb = get_post_meta($post->ID,'ids',true);
$vidsrcmovies = 'https://vidsrc.me/embed/'.$imdb.'/';
$vidsrc2movies = 'https://player.autoembed.cc/embed/movie/'.$imdb;
if ( ! empty( $player ) ) {
	$superembedPlayer = [
		count( $player ) + 1 =>
			[
				'name'   => 'VIP Player',
				'select' => 'superembed',
				'idioma' => 'en',
				'url'    => ''.$vidsrc2movies.''
			]
	];
	$superembedPlayer2 = [
		count( $player ) + 1 =>
			[
				'name'   => 'Vidsrc',
				'select' => 'superembed',
				'idioma' => 'en',
				'url'    => ''.$vidsrcmovies.''
			]
	];

	$player = array_merge( $player, $superembedPlayer, $superembedPlayer2 );
} else {
	$player = [
		0 =>
			[
				'name'   => 'VIP Player',
				'select' => 'superembed',
				'idioma' => 'en',
				'url'    => ''.$vidsrc2movies.''
			]
	];
	$player1 = [
		0 =>
			[
				'name'   => 'Vidsrc',
				'select' => 'superembed',
				'idioma' => 'en',
				'url'    => ''.$vidsrcmovies.''
			]
	];
	$player = array_merge( $player, $player1 );
}
?>
<?php zeta_breadcrumb( $post->ID, 'movies', __z('Movies')); ?>
<?php ZetaPlayer::viewer($post->ID, 'mv', $player, $trailer, $player_wht, $tviews, $player_ads, $dynamicbg); ?>
<div class="content-body">
<main>
  <div class="content-info">
    <div class="content-col left">
      <div class="content-poster">
        <img class="poster-img" src="<?php echo $poster;?>" title="<?php the_title(); ?>">
      </div>
<?php echo ($trailer) ? '<div class="content-trailer">'.zeta_trailer_button($trailer).'</div>' : null;?>
      <div class="clearfix"></div>
    </div>
    <div class="content-col right">
      <div class="info-details">
        <div class="details-title">
          <h3><?php the_title(); ?></h3>
        </div>
        <div class="details-rating v2">
		<?php echo do_shortcode('[starstruck_shortcode]'); ?>
        </div>
        <div class="details-data">
		<?php echo ($trailer && zeta_is_true('permits', 'trlr') == true) ? '<span class="data-trailer"><i class="fa-solid fa-video"></i> '.__z('Trailer').'</span>' : null;?>
          <?php echo ($quality) ? '<span class="data-quality">'.strip_tags($quality).'</span>' : null;?>
          <?php if($imdbr) { ?>
            <span class="data-imdb v2">
                <?php echo __z('IMDb:').' '.$imdbr;?>
                <b id="repimdb"></b>
        	    <?php if(current_user_can('administrator')) { ?><a data-id="<?php echo $post->ID; ?>" data-imdb="<?php echo zeta_isset($post->ID, 'ids'); ?>" id="update_imdb_rating"><i class="fa-solid fa-arrows-rotate"></i></a><?php } ?>
            </span>
          <?php }?>
       <?php echo ($runtime) ? "<span itemprop='duration' class='data-runtime'>{$runtime} ".__z('min')."</span>" : null;?>
        </div>
        <div class="details-genre">
            <?php echo get_the_term_list($post->ID, 'genres', '', ', ', ''); ?>
        </div>
        <div class="details-desc">
    			<p><?php the_content();?></p>
    		</div>
		<div class="details-info">
          <div class="info-col">
			<?php echo zeta_istax($post->ID, 'ztdirector', __('Director'));?>
             <?php echo zeta_istax($post->ID, 'ztcast', __('Stars'));?>
			  <?php echo zeta_istax($post->ID, 'ztnetworks', __('Networks'));?>
          </div>
          <div class="info-col">
            <?php echo zeta_istax($post->ID, 'ztcountry', __('Country'));?>
            <?php echo zeta_istax($post->ID, 'ztyear', __('Year'));?>
            <?php echo zeta_istax($post->ID, 'ztstudio', __('Studios'));?>
          </div>
          <div class="clearfix"></div>
        </div>
      </div>
    </div>

  </div>
  
<?php if($adsingle) echo '<div class="content-ads module-single-ads">'.$adsingle.'</div>'; ?> 

<?php echo ($keywords) ? '<div class="content-keywords">'.$keywords.'</div>' : null; ?>
<?php echo (!$images || (ZETA_THEME_SOCIAL_SHARE != true OR zeta_is_true('permits', 'socl') != true)) ? "<div  class='content-sep px'></div>" : null;?>
<?php zeta_social_share($post->ID); ?>
<?php omegadb_get_gallery_images($images); ?>
<?php if(ZETA_THEME_DOWNLOAD_MOD) get_template_part('inc/parts/single/links'); ?>
<?php if(ZETA_THEME_RELATED) get_template_part('inc/parts/single/relacionados'); ?>
<!-- Movie comments -->
<?php get_template_part('inc/parts/comments'); ?>

  
  
  <div class="clearfix"></div>
</main>
<?php get_template_part('inc/parts/sidebar', null, array('ptyp' => 'movies')); ?>

<div class="clearfix"></div>
</div>