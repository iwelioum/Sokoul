<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/

if(post_password_required()){
	return;
}
?>

<div id="comments" class="content-comments">
    <div class="content-title">
      <span class="title-head">Comments</span>
      <!--<span class="comments-count">(10)</span>
      <span class="title-sep"></span>-->
    </div>
	<div class="comments-wrapper">
<?php global $comments_open, $comments_closed_notice; if ( ! $comments_open ) { echo $comments_closed_notice; } $comments_args = zt_theme_comments_args(); comment_form( $comments_args ); ?>
	<?php if ( have_comments() ) : ?>
	<?php echo '<div class="comments-lists">';
				$args = zt_comments_args();
				wp_list_comments( $args );
			echo '</div>';
	?>
	<?php endif; // have_comments() ?>
	</div>
</div>




















