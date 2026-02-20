<?php
/**
 * @since 1.0.0
 * @version 2.0
 */

CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('Avatars'),
        'parent' => 'customize',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'id' => 'avatar_source',
                'type' => 'radio',
                'title' => __z('Image Source'),
                'default' => 'local',
                'options' => array(
                    'local' => __z('Avatar Galleries'),
                    'gravatar'  => __z('Gravatar')
                )
            ),
			array(
			  'type'    => 'submessage',
			  'style'   => 'danger',
			  'content' => 'Avatar gallery selection is only possible with <strong>local</strong> image source.',
			  'dependency' => array('avatar_source', '==', 'gravatar')
			),
			array(
			'type'  => 'subheading',
			'title' => 'Avatar Galleries'
			),

			  array(
			  'id'     => 'avatar-gallery',
			  'type'   => 'group',
			  'fields' => array(

				array(
				  'id'    => 'gallery-name',
				  'type'  => 'text',
				  'title' => 'Gallery Name'
				),
				array(
				  'id'          => 'gallery-images',
				  'type'        => 'gallery',
				  'add_title'   => 'Add Images',
				  'edit_title'  => 'Edit Images',
				  'clear_title' => 'Remove Images',
				),

			  ),
			),
        )
    )
);
