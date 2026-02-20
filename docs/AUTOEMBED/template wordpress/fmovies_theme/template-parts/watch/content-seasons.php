<?php
/**
 * Displays seasons & episodes
 *
 * @package fmovie
 */

$fmovie_premium = get_option('admin_premium');
$fmovie_admin_server_2 = get_option('admin_server_2');
$fmovie_admin_server_3 = get_option('admin_server_3');
?>

<?php if ( get_field( 'manual_tv' ) == 1 ) : ?>
<div id="servers">
	<div class="note">
		<?php echo textautoembed; ?>
	</div>
	<span class="server_container" id="s1_1">
	</span>
</div><!--/#servers-->
<div class="groups">
	<div id="seasons" class="dropdown">
		<button class="btn" data-toggle="dropdown"></button>
		<ul class="dropdown-menu"></ul>
	</div><!--/#seasons-->
	<ul id="ranges"></ul>
	<div class="clearfix"></div>
</div><!--/#groups-->
<div class="tv-details-episodes episodes" style="display: block;">
	<div class="range"></div>
	<div class="clearfix"></div>
</div><!--/#episodes-->	
<?php else : ?>
<div id="servers">
	<div class="note">
		<?php echo textautoembed; ?>
	</div>
<?php if ($fmovie_premium == 1) {  ?>
	<div class="server active" data-load-embed="<?php tmdb_id(); ?>" data-load-embed-host="premium" data-load-season="1" data-load-episode="1">
		<span><?php echo esc_html__( 'Server', 'fmovie' ) ?></span>
		<div><?php echo server_0_text; ?></div>
	</div><!-- /#premium api -->
	<div class="server" data-load-embed="<?php tmdb_id(); ?>" data-load-embed-host="embedru" data-load-season="1" data-load-episode="1">
		<span><?php echo esc_html__( 'Server', 'fmovie' ) ?></span>
		<div><?php echo server_1_text; ?></div>
	</div>
<?php } else { ?>
	<div class="server active" data-load-embed="<?php tmdb_id(); ?>" data-load-embed-host="embedru" data-load-season="1" data-load-episode="1">
		<span><?php echo esc_html__( 'Server', 'fmovie' ) ?></span>
		<div><?php echo server_1_text; ?></div>
	</div>
<?php } ?>
<?php if ($fmovie_admin_server_2 == 1) {  ?>
	<div class="server" data-load-embed="<?php tmdb_id(); ?>" data-load-embed-host="superembed" data-load-season="1" data-load-episode="1">
		<span><?php echo esc_html__( 'Server', 'fmovie' ) ?></span>
		<div><?php echo server_2_text; ?></div>
		</div>
<?php } ?>
<?php if ($fmovie_admin_server_3 == 1) {  ?>
	<div class="server" data-load-embed="<?php tmdb_id(); ?>" data-load-embed-host="vidsrc" data-load-season="1" data-load-episode="1">
		<span><?php echo esc_html__( 'Server', 'fmovie' ) ?></span>
		<div><?php echo server_3_text; ?></div>
	</div>
<?php } ?>
</div><!--/#servers-->

<div class="groups">
	<div id="seasons" class="dropdown">
		<button class="btn" data-toggle="dropdown"></button>
		<ul class="dropdown-menu"></ul>
	</div><!--/#seasons-->
	<ul id="ranges"></ul>
	<div class="clearfix"></div>
</div><!--/#groups-->

<div class="tv-details-episodes episodes" style="display: block;">
	<div class="range"></div>
	<div class="clearfix"></div>
</div><!--/#episodes-->	
<?php endif; ?>